package us.dot.its.jpo.ode.api.config;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;

class RsuRebootConfigTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "rsu-reboot.concurrency=0", "rsu-reboot.concurrency=33",
            "rsu-reboot.port=0", "rsu-reboot.port=65536",
            "rsu-reboot.timeout=0s", "rsu-reboot.timeout=121s"
    })
    void invalidResourceLimitsPreventStartup(String property) {
        new ApplicationContextRunner()
                .withUserConfiguration(RsuRebootConfig.class)
                .withPropertyValues(property)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class);
                });
    }

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
