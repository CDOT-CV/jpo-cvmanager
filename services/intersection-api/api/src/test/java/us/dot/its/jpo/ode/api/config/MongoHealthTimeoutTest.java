package us.dot.its.jpo.ode.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

class MongoHealthTimeoutTest {

    @Test
    @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void mongoHealthCheckTimesOutWhenServerIsUnavailable() {
        try (MongoClient client = MongoClients.create("mongodb://127.0.0.1:1")) {
            HealthIndicator indicator = new MongoHealthConfig().mongoHealthIndicator(client, "test");

            long start = System.nanoTime();
            Health health = indicator.health();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(elapsed).isBetween(Duration.ofSeconds(4), Duration.ofSeconds(12));
        }
    }
}
