package us.dot.its.jpo.ode.api.config;

import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class EmailHealthEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "emailHealthContributors";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String emailBroker = environment.getProperty("email.broker", "SMTP");
        boolean emailEnabled = environment.getProperty("enable.email", Boolean.class, true);
        boolean smtpEnabled = "SMTP".equalsIgnoreCase(emailBroker.trim()) && emailEnabled;
        boolean postmarkEnabled = "POSTMARK".equalsIgnoreCase(emailBroker.trim()) && emailEnabled;
        boolean sendGridEnabled = "SENDGRID".equalsIgnoreCase(emailBroker.trim()) && emailEnabled;

        environment.getPropertySources().addFirst(new MapPropertySource(
                PROPERTY_SOURCE_NAME,
                Map.of(
                        "management.health.mail.enabled", smtpEnabled,
                        "management.health.postmark.enabled", postmarkEnabled,
                        "management.health.sendgrid.enabled", sendGridEnabled)));
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
