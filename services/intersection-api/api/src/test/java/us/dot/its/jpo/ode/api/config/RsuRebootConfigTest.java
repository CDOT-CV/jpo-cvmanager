package us.dot.its.jpo.ode.api.config;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RsuRebootConfigTest {
    @Test
    void dedicatedPoolDoesNotSuppressBootsApplicationExecutor() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
                .withUserConfiguration(RsuRebootConfig.class)
                .run(context -> {
                    assertNotNull(context.getBean("applicationTaskExecutor"));
                    assertNotSame(context.getBean("applicationTaskExecutor"), context.getBean("rsuRebootExecutor"));
                });
    }
}
