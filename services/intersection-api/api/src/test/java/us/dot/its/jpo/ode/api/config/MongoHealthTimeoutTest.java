package us.dot.its.jpo.ode.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

class MongoHealthTimeoutTest {

    @Test
    void indicatorUsesConfiguredDatabaseFactoryWithoutMongoClientBean() {
        MongoDatabaseFactory factory = mock(MongoDatabaseFactory.class);
        MongoDatabase database = mock(MongoDatabase.class);
        MongoDatabase timedDatabase = mock(MongoDatabase.class);
        when(factory.getMongoDatabase()).thenReturn(database);
        when(database.withTimeout(5, TimeUnit.SECONDS)).thenReturn(timedDatabase);
        when(timedDatabase.runCommand(new Document("hello", 1))).thenReturn(new Document("ok", 1));

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(MongoDatabaseFactory.class, () -> factory);
            context.register(MongoHealthConfig.class);
            context.refresh();

            assertThat(context.getBeansOfType(MongoClient.class)).isEmpty();
            assertThat(context.getBean("mongoHealthIndicator", HealthIndicator.class).health().getStatus())
                    .isEqualTo(Status.UP);
            verify(timedDatabase).runCommand(new Document("hello", 1));
        }
    }

    @Test
    void indicatorReportsDatabaseErrors() {
        MongoDatabaseFactory factory = mock(MongoDatabaseFactory.class);
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(factory.getMongoDatabase()).thenThrow(failure);

        Health health = new MongoHealthConfig().mongoHealthIndicator(factory).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("error", failure.toString());
    }

    @Test
    @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void mongoHealthCheckTimesOutWhenServerIsUnavailable() {
        try (MongoClient client = MongoClients.create("mongodb://127.0.0.1:1")) {
            MongoDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(client, "test");
            HealthIndicator indicator = new MongoHealthConfig().mongoHealthIndicator(factory);

            long start = System.nanoTime();
            Health health = indicator.health();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(elapsed).isBetween(Duration.ofSeconds(4), Duration.ofSeconds(12));
        }
    }
}
