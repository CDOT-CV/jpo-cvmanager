package us.dot.its.jpo.ode.api.emails.health;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import com.postmarkapp.postmark.client.ApiClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostmarkHealthIndicatorTest {

    private final ApiClient postmark = mock(ApiClient.class);
    private final PostmarkHealthIndicator indicator = new PostmarkHealthIndicator(postmark);

    @Test
    void reportsUpWhenServerEndpointResponds() throws Exception {
        when(postmark.getServer()).thenReturn(null);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        verify(postmark).getServer();
    }

    @Test
    void reportsDownWhenServerEndpointFails() throws Exception {
        when(postmark.getServer()).thenThrow(new IOException("Connection failed"));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }
}
