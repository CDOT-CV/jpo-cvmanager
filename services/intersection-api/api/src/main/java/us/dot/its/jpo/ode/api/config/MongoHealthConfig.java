package us.dot.its.jpo.ode.api.config;

import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mongodb.client.MongoClient;

/**
 * Replaces Boot's default Mongo health indicator (it backs off when a bean named
 * "mongoHealthIndicator" exists) so the health check has its own 5s timeout
 * without capping normal MongoDB operations.
 */
@Configuration(proxyBeanMethods = false)
public class MongoHealthConfig {

    private static final long MONGO_HEALTH_CHECK_TIMEOUT_SECONDS = 5;

    @Bean
    HealthIndicator mongoHealthIndicator(MongoClient mongoClient,
            @Value("${spring.mongodb.database}") String database) {
        return () -> {
            try {
                mongoClient.getDatabase(database)
                        .withTimeout(MONGO_HEALTH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .runCommand(new Document("hello", 1));
                return Health.up().build();
            } catch (RuntimeException exception) {
                return Health.down().withException(exception).build();
            }
        };
    }
}