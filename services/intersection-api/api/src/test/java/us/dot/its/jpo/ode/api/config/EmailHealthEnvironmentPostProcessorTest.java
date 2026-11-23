package us.dot.its.jpo.ode.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class EmailHealthEnvironmentPostProcessorTest {

    private final EmailHealthEnvironmentPostProcessor postProcessor = new EmailHealthEnvironmentPostProcessor();

    @Test
    void enablesMailHealthWhenSmtpIsActiveAndEmailIsEnabled() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("email.broker", "SMTP")
                .withProperty("enable.email", "true");

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("management.health.mail.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("management.health.postmark.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("management.health.sendgrid.enabled", Boolean.class)).isFalse();
    }

    @Test
    void disablesMailHealthWhenAnotherBrokerIsActive() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("email.broker", "SENDGRID")
                .withProperty("enable.email", "true");

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("management.health.mail.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("management.health.postmark.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("management.health.sendgrid.enabled", Boolean.class)).isTrue();
    }

    @Test
    void enablesPostmarkHealthWhenPostmarkIsActiveAndEmailIsEnabled() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("email.broker", "POSTMARK")
                .withProperty("enable.email", "true");

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("management.health.mail.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("management.health.postmark.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("management.health.sendgrid.enabled", Boolean.class)).isFalse();
    }

    @Test
    void disablesMailHealthWhenEmailIsDisabled() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("email.broker", "SMTP")
                .withProperty("enable.email", "false");

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("management.health.mail.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("management.health.postmark.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("management.health.sendgrid.enabled", Boolean.class)).isFalse();
    }

    @Test
    void usesSmtpAndEnabledDefaultsWhenPropertiesAreMissing() {
        MockEnvironment environment = new MockEnvironment();

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("management.health.mail.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("management.health.postmark.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("management.health.sendgrid.enabled", Boolean.class)).isFalse();
    }
}
