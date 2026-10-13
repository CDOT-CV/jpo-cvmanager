package us.dot.its.jpo.ode.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
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
}
