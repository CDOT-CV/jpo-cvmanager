package us.dot.its.jpo.ode.api.emails.health;

import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;

import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnProperty(name = "management.health.sendgrid.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SendGridHealthIndicator implements HealthIndicator {

    private final SendGrid sendGrid;

    @Override
    public Health health() {
        Request request = new Request();
        request.setMethod(Method.GET);
        request.setEndpoint("scopes");
        try {
            Response response = sendGrid.api(request);
            int statusCode = response.getStatusCode();
            if (statusCode >= 200 && statusCode < 300) {
                return Health.up().build();
            }
            return Health.down().withDetail("statusCode", statusCode).build();
        } catch (IOException | RuntimeException exception) {
            return Health.down().withException(exception).build();
        }
    }
}
