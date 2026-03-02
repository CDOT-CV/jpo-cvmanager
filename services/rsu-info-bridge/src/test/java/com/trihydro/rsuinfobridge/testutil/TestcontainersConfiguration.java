package com.trihydro.rsuinfobridge.testutil;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Test configuration that provides a PostGIS-enabled PostgreSQL container
 * initialized with the production CVManager_CreateTables.sql schema.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final DockerImageName POSTGIS_IMAGE = DockerImageName
            .parse("postgis/postgis:15-3.4-alpine")
            .asCompatibleSubstituteFor("postgres");

    @Bean
    @ServiceConnection
    @SuppressWarnings("resource")
    public PostgreSQLContainer<?> postgisContainer() {
        Path schemaPath = findSchemaFile();

        return new PostgreSQLContainer<>(POSTGIS_IMAGE)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(schemaPath),
                        "/docker-entrypoint-initdb.d/01-schema.sql"
                );
    }

    /**
     * Finds the CVManager_CreateTables.sql file by searching from the current working directory.
     * Handles both running from module directory (rsu-info-bridge) and from project root.
     */
    private Path findSchemaFile() {
        Path currentDir = Paths.get("").toAbsolutePath();

        // Try different possible locations
        Path[] possiblePaths = {
                // Running from rsu-info-bridge directory
                currentDir.resolve("../../resources/sql_scripts/CVManager_CreateTables.sql").normalize(),
                // Running from services directory
                currentDir.resolve("../resources/sql_scripts/CVManager_CreateTables.sql").normalize(),
                // Running from project root
                currentDir.resolve("resources/sql_scripts/CVManager_CreateTables.sql").normalize(),
        };

        for (Path path : possiblePaths) {
            if (Files.exists(path)) {
                return path;
            }
        }

        throw new IllegalStateException(
                "Could not find CVManager_CreateTables.sql. Current directory: " + currentDir +
                        ". Searched paths: " + java.util.Arrays.toString(possiblePaths));
    }
}
