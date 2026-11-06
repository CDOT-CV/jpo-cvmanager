package us.dot.its.jpo.ode.api.emails.health;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SendGridHealthIndicatorTest {

    private final SendGrid sendGrid = mock(SendGrid.class);
    private final SendGridHealthIndicator indicator = new SendGridHealthIndicator(sendGrid);

    @Test
    void reportsUpAndCallsReadOnlyScopesEndpoint() throws Exception {
        when(sendGrid.api(org.mockito.ArgumentMatchers.any(Request.class))).thenReturn(response(200));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        verify(sendGrid).api(argThat(request -> request.getMethod() == Method.GET
                && "scopes".equals(request.getEndpoint())));
    }

    @Test
    void reportsDownWhenScopesEndpointReturnsFailure() throws Exception {
        when(sendGrid.api(org.mockito.ArgumentMatchers.any(Request.class))).thenReturn(response(401));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void reportsDownWhenScopesEndpointCannotBeReached() throws Exception {
        when(sendGrid.api(org.mockito.ArgumentMatchers.any(Request.class)))
                .thenThrow(new IOException("Connection failed"));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    private Response response(int statusCode) {
        Response response = new Response();
        response.setStatusCode(statusCode);
        return response;
    }
}
