package us.dot.its.jpo.ode.api.emails.health;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import com.postmarkapp.postmark.client.ApiClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostmarkHealthIndicatorTest {

    private final ApiClient postmark = mock(ApiClient.class);
    private final PostmarkHealthIndicator indicator = new PostmarkHealthIndicator(postmark);

    @AfterEach
    void closeIndicator() {
        indicator.close();
    }

    @Test
    void reportsUpWhenServerEndpointResponds() throws Exception {
        when(postmark.getServer()).thenReturn(null);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        verify(postmark).getServer();
    }

    @Test
    void reportsDownWhenServerEndpointFails() throws Exception {
        when(postmark.getServer()).thenThrow(new IOException("Connection failed"));

        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("error").toString()).contains("Connection failed");
    }

    @Test
    @Timeout(value = 15, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void timeoutIsSharedAndBoundsUninterruptibleCheckUntilItFinishes() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                started.countDown();
                try {
                    boolean done = false;
                    while (!done) {
                        try {
                            release.await();
                            done = true;
                        } catch (InterruptedException ignored) {
                            // Model an SDK operation that does not respond to interruption.
                        }
                    }
                } finally {
                    finished.countDown();
                }
            }
            return null;
        }).when(postmark).getServer();

        try {
            long start = System.nanoTime();
            Future<Health> first = callers.submit(() -> indicator.health());
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(1000);
            long joinedAt = System.nanoTime();
            Future<Health> concurrent = callers.submit(() -> indicator.health());
            Health timedOut = first.get(7, TimeUnit.SECONDS);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            assertThat(timedOut.getStatus()).isEqualTo(Status.DOWN);
            assertThat(timedOut.getDetails().get("error")).asString().contains("timed out");
            assertThat(elapsed).isBetween(Duration.ofSeconds(4), Duration.ofSeconds(6));
            assertThat(concurrent.get(1, TimeUnit.SECONDS)).isEqualTo(timedOut);
            assertThat(Duration.ofNanos(System.nanoTime() - joinedAt)).isLessThan(Duration.ofMillis(4800));

            long checkedAt = System.nanoTime();
            Health busy = indicator.health();
            assertThat(busy.getStatus()).isEqualTo(Status.DOWN);
            assertThat(busy.getDetails().get("error")).asString().contains("timed out");
            assertThat(Duration.ofNanos(System.nanoTime() - checkedAt)).isLessThan(Duration.ofSeconds(1));
            assertThat(calls.get()).isEqualTo(1);

            release.countDown();
            assertThat(finished.await(1, TimeUnit.SECONDS)).isTrue();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            Health recovered;
            do {
                recovered = indicator.health();
                if (recovered.getStatus().equals(Status.UP)) {
                    break;
                }
                Thread.sleep(10);
            } while (System.nanoTime() < deadline);
            assertThat(recovered.getStatus()).isEqualTo(Status.UP);
            assertThat(calls.get()).isEqualTo(2);
        } finally {
            release.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void concurrentChecksShareSuccessfulResult() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        doAnswer(invocation -> {
            started.countDown();
            assertThat(release.await(3, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(postmark).getServer();

        try {
            Future<Health> first = callers.submit(() -> indicator.health());
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            CountDownLatch secondStarted = new CountDownLatch(1);
            Future<Health> second = callers.submit(() -> {
                secondStarted.countDown();
                return indicator.health();
            });
            assertThat(secondStarted.await(2, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100);
            assertThat(second.isDone()).isFalse();
            release.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS).getStatus()).isEqualTo(Status.UP);
            assertThat(second.get(2, TimeUnit.SECONDS).getStatus()).isEqualTo(Status.UP);
            verify(postmark).getServer();
        } finally {
            release.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    void interruptionIsPreserved() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            release.await();
            return null;
        }).when(postmark).getServer();
        Thread.currentThread().interrupt();
        try {
            Health result = indicator.health();
            assertThat(result.getStatus()).isEqualTo(Status.DOWN);
            assertThat(result.getDetails().get("error")).asString().contains("InterruptedException");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
            release.countDown();
        }
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void interruptedCallerDoesNotCancelSharedCheck() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService callers = Executors.newSingleThreadExecutor();
        doAnswer(invocation -> {
            started.countDown();
            release.await();
            return null;
        }).when(postmark).getServer();

        try {
            Future<Health> first = callers.submit(() -> indicator.health());
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            Thread.currentThread().interrupt();
            try {
                assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                Thread.interrupted();
            }
            release.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS).getStatus()).isEqualTo(Status.UP);
            verify(postmark).getServer();
        } finally {
            release.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void shutdownReleasesWaitingCaller() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        ExecutorService callers = Executors.newSingleThreadExecutor();
        doAnswer(invocation -> {
            started.countDown();
            new CountDownLatch(1).await();
            return null;
        }).when(postmark).getServer();

        try {
            Future<Health> result = callers.submit(() -> indicator.health());
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            indicator.close();
            Health health = result.get(2, TimeUnit.SECONDS);
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails().get("error")).asString().contains("shutting down");
            assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        } finally {
            callers.shutdownNow();
        }
    }
}
