package us.dot.its.jpo.ode.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulingConfig {

    public static final String EMAIL_TASK_SCHEDULER = "emailTaskScheduler";
    public static final String REPORT_TASK_SCHEDULER = "reportTaskScheduler";

    @Bean(name = EMAIL_TASK_SCHEDULER)
    public TaskScheduler emailTaskScheduler() {
        return singleThreadScheduler("email-scheduler-");
    }

    @Bean(name = REPORT_TASK_SCHEDULER)
    public TaskScheduler reportTaskScheduler() {
        return singleThreadScheduler("report-scheduler-");
    }

    private static ThreadPoolTaskScheduler singleThreadScheduler(String threadNamePrefix) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(threadNamePrefix);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
