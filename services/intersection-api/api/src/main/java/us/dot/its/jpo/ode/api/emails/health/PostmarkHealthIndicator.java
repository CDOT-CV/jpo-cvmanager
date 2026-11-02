package us.dot.its.jpo.ode.api.emails.health;

import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.postmarkapp.postmark.client.ApiClient;
import com.postmarkapp.postmark.client.exception.PostmarkException;

import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnProperty(name = "management.health.postmark.enabled", havingValue = "true")
@RequiredArgsConstructor
public class PostmarkHealthIndicator implements HealthIndicator {

    private final ApiClient postmark;

    @Override
    public Health health() {
        try {
            postmark.getServer();
            return Health.up().build();
        } catch (PostmarkException | IOException | RuntimeException exception) {
            return Health.down().withException(exception).build();
        }
    }
}
