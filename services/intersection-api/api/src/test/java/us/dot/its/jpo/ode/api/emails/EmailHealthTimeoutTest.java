package us.dot.its.jpo.ode.api.emails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.Configurable;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.test.util.ReflectionTestUtils;

import com.postmarkapp.postmark.client.ApiClient;
import com.sendgrid.Client;
import com.sendgrid.SendGrid;
import com.sun.net.httpserver.HttpServer;

import us.dot.its.jpo.ode.api.emails.health.PostmarkHealthIndicator;
import us.dot.its.jpo.ode.api.emails.health.SendGridHealthIndicator;

class EmailHealthTimeoutTest {

    @Test
    @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @SuppressWarnings("deprecation")
    void postmarkHealthCheckTimesOutOnStalledResponse() throws Exception {
        EmailProperties properties = new EmailProperties();
        EmailProperties.PostmarkProperties postmarkProperties = new EmailProperties.PostmarkProperties();
        postmarkProperties.setApiKey("test-token");
        properties.setPostmark(postmarkProperties);
        ApiClient client = properties.postmarkHealthCheck();

        org.apache.hc.client5.http.config.RequestConfig.Builder builder =
                (org.apache.hc.client5.http.config.RequestConfig.Builder) ReflectionTestUtils
                        .getField(client.getHttpClient(), "clientConfigBuilder");
        org.apache.hc.client5.http.config.RequestConfig config = builder.build();
        assertThat(config.getConnectTimeout().toMilliseconds()).isEqualTo(5_000);
        assertThat(config.getResponseTimeout().toMilliseconds()).isEqualTo(5_000);

        try (StalledServer server = new StalledServer()) {
            ReflectionTestUtils.setField(client, "baseUrl", server.address());
            client.setSecureConnection(false);
            assertTimeout(new PostmarkHealthIndicator(client).health(), server, "/server/");
        } finally {
            client.getHttpClient().getClient().close();
        }
    }

    @Test
    @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void sendGridHealthCheckTimesOutOnStalledResponse() throws Exception {
        EmailProperties properties = new EmailProperties();
        EmailProperties.SendgridProperties sendgridProperties = new EmailProperties.SendgridProperties();
        sendgridProperties.setApiKey("test-token");
        properties.setSendgrid(sendgridProperties);
        SendGrid sendGrid = properties.sendGridHealthCheck();
        Client client = (Client) ReflectionTestUtils.getField(sendGrid, "client");
        CloseableHttpClient httpClient = (CloseableHttpClient) ReflectionTestUtils.getField(client, "httpClient");
        RequestConfig config = ((Configurable) httpClient).getConfig();
        assertThat(config.getConnectionRequestTimeout()).isEqualTo(5_000);
        assertThat(config.getConnectTimeout()).isEqualTo(5_000);
        assertThat(config.getSocketTimeout()).isEqualTo(5_000);

        try (StalledServer server = new StalledServer(); Client redirectedClient = spy(client)) {
            doAnswer(invocation -> URI.create("http://" + server.address() + invocation.getArgument(1)))
                    .when(redirectedClient).buildUri(anyString(), anyString(), any());
            ReflectionTestUtils.setField(sendGrid, "client", redirectedClient);
            assertTimeout(new SendGridHealthIndicator(sendGrid).health(), server, "/v3/scopes");
        }
    }

    private static void assertTimeout(Health health, StalledServer server, String expectedPath) {
        assertThat(server.requestPath).isEqualTo(expectedPath);
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("error").toString()).contains("SocketTimeoutException");
        assertThat(Duration.ofNanos(System.nanoTime() - server.requestStartedNanos))
                .isBetween(Duration.ofSeconds(4), Duration.ofSeconds(12));
    }

    private static final class StalledServer implements AutoCloseable {
        private final CountDownLatch release = new CountDownLatch(1);
        private final HttpServer server;
        private volatile String requestPath;
        private volatile long requestStartedNanos;

        private StalledServer() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                requestPath = exchange.getRequestURI().getPath();
                requestStartedNanos = System.nanoTime();
                try {
                    release.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();
        }

        private String address() {
            return "127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            release.countDown();
            server.stop(0);
        }
    }
}
