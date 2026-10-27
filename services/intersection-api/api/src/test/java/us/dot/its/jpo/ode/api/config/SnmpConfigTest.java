package us.dot.its.jpo.ode.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.FileSystemResource;

class SnmpConfigTest {
    private ApplicationContextRunner context() throws IOException {
        // Load the production YAML explicitly; tests also have an application.yaml on the classpath.
        var sources = new YamlPropertySourceLoader().load("application",
                new FileSystemResource("src/main/resources/application.yaml"));
        return new ApplicationContextRunner()
                .withUserConfiguration(SnmpConfig.class)
                .withBean(Clock.class, Clock::systemUTC)
                .withPropertyValues("enable.api=true")
                .withInitializer(context -> sources.forEach(source -> context.getEnvironment()
                        .getPropertySources().addLast(source)));
    }

    @Test
    void usesFiveSecondRequestTimeoutByDefault() throws IOException {
        context().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SnmpProperties.class).taskOptions().requestPolicy().requestTimeout())
                    .isEqualTo(Duration.ofSeconds(5));
        });
    }

    @Test
    void deploymentTimeoutOverridesTheRequestPolicyWithoutResettingTheTaskBudget() throws IOException {
        context().withPropertyValues("SNMP_REQUEST_TIMEOUT=10s").run(context -> {
            assertThat(context).hasNotFailed();
            var options = context.getBean(SnmpProperties.class).taskOptions();
            assertThat(options.requestPolicy().requestTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(options.executionTimeout()).isEqualTo(Duration.ofSeconds(60));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "-1s", "25h", "not-a-duration"})
    void invalidDeploymentTimeoutPreventsStartup(String timeout) throws IOException {
        context().withPropertyValues("SNMP_REQUEST_TIMEOUT=" + timeout).run(context -> {
            assertThat(context).hasFailed();
        });
    }
}
