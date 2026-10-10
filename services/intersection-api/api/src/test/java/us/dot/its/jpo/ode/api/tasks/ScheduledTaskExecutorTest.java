package us.dot.its.jpo.ode.api.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import us.dot.its.jpo.ode.api.config.SchedulingConfig;

class ScheduledTaskExecutorTest {

    @Test
    void emailTasksUseEmailScheduler() {
        assertScheduler(EmailTask.class, "sendHourlyNotifications", SchedulingConfig.EMAIL_TASK_SCHEDULER);
        assertScheduler(EmailTask.class, "sendDailyNotifications", SchedulingConfig.EMAIL_TASK_SCHEDULER);
        assertScheduler(EmailTask.class, "sendWeeklyNotifications", SchedulingConfig.EMAIL_TASK_SCHEDULER);
        assertScheduler(EmailTask.class, "sendMonthlyNotifications", SchedulingConfig.EMAIL_TASK_SCHEDULER);
    }

    @Test
    void reportTasksUseReportScheduler() {
        assertScheduler(ReportTask.class, "generateDailyReports", SchedulingConfig.REPORT_TASK_SCHEDULER);
        assertScheduler(ReportTask.class, "generateWeeklyReports", SchedulingConfig.REPORT_TASK_SCHEDULER);
        assertScheduler(ReportTask.class, "generateMonthlyReports", SchedulingConfig.REPORT_TASK_SCHEDULER);
    }

    private static void assertScheduler(Class<?> taskClass, String methodName, String schedulerName) {
        try {
            Method method = taskClass.getDeclaredMethod(methodName);
            Scheduled scheduled = method.getAnnotation(Scheduled.class);
            assertThat(scheduled).isNotNull();
            assertThat(scheduled.scheduler()).isEqualTo(schedulerName);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }
}
