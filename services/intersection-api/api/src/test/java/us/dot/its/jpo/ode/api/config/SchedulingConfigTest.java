package us.dot.its.jpo.ode.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class SchedulingConfigTest {

    private final SchedulingConfig config = new SchedulingConfig();

    @Test
    void emailAndReportTasksUseIsolatedSingleThreadSchedulers() {
        ThreadPoolTaskScheduler emailScheduler = (ThreadPoolTaskScheduler) config.emailTaskScheduler();
        ThreadPoolTaskScheduler reportScheduler = (ThreadPoolTaskScheduler) config.reportTaskScheduler();

        assertThat(emailScheduler.getPoolSize()).isEqualTo(1);
        assertThat(emailScheduler.getThreadNamePrefix()).isEqualTo("email-scheduler-");
        assertThat(reportScheduler.getPoolSize()).isEqualTo(1);
        assertThat(reportScheduler.getThreadNamePrefix()).isEqualTo("report-scheduler-");
    }

    @ParameterizedTest
    @ValueSource(strings = { "email", "report" })
    void shutdownFinishesRunningWorkWithoutWaitingForFutureRuns(String schedulerName) throws Exception {
        var scheduler = (ThreadPoolTaskScheduler) (schedulerName.equals("email")
                ? config.emailTaskScheduler() : config.reportTaskScheduler());
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var shutdownFinished = new CountDownLatch(1);
        var interrupted = new AtomicBoolean();
        var futureTaskRan = new AtomicBoolean();
        scheduler.initialize();
        try {
            scheduler.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    interrupted.set(true);
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
            var futureRun = scheduler.schedule(() -> futureTaskRan.set(true), Instant.now().plusSeconds(86400));
            var shutdown = Thread.ofVirtual().start(() -> {
                scheduler.shutdown();
                shutdownFinished.countDown();
            });
            try {
                // Shutdown must still allow a task already sending email or building a report to finish.
                assertThat(shutdownFinished.await(100, TimeUnit.MILLISECONDS)).isFalse();
                assertThat(interrupted).isFalse();
                release.countDown();

                // A run scheduled for tomorrow must not consume the 30-second shutdown budget.
                assertThat(shutdownFinished.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(futureRun.isCancelled()).isTrue();
                assertThat(futureTaskRan).isFalse();
                assertThat(interrupted).isFalse();
                assertThat(scheduler.getScheduledThreadPoolExecutor().isTerminated()).isTrue();
            } finally {
                release.countDown();
                futureRun.cancel(false);
                scheduler.getScheduledThreadPoolExecutor().shutdownNow();
                shutdown.join(3000);
            }
        } finally {
            release.countDown();
            scheduler.shutdown();
        }
    }
}
