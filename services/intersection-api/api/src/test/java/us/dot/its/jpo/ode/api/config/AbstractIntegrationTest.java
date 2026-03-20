package us.dot.its.jpo.ode.api.config;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests that require PostgreSQL and MongoDB
 * containers.
 * Uses Testcontainers to spin up real database instances for testing.
 * 
 * Containers are started once and shared across all test classes that extend
 * this base class.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TestConfiguration
@Testcontainers
public class TestcontainersConfiguration {

      @Bean
      @ServiceConnection
      PostgreSQLContainer<?> postgresContainer() {
          return new PostgreSQLContainer<>(
                  DockerImageName.parse("postgis/postgis:15-master")
                          .asCompatibleSubstituteFor("postgres"));
      }

      @Bean
      @ServiceConnection
      MongoDBContainer mongoDbContainer() {
          return new MongoDBContainer(DockerImageName.parse("mongo:8"));
      }

}