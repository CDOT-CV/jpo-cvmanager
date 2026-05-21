# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Service Overview

`rsu-info-bridge` — Spring Boot 4.0.3 / Java 25 microservice that exposes a read-only HTTP interface over the CV Manager PostgreSQL schema for external consumers needing RSU metadata (e.g. Corvus / CHS, TIM tooling). Maven module: `com.trihydro:rsu-info-bridge`. Default port `16543`. Parent project: `../../CLAUDE.md` (`jpo-cvmanager`).

## Build & Run

Maven wrapper is the canonical entry point — do not invoke a system `mvn`.

```sh
./mvnw clean install                                  # build + tests (Docker Engine required for Testcontainers)
./mvnw test                                           # all tests
./mvnw test -Dtest=RsuServiceTest                     # single class
./mvnw test -Dtest=RsuServiceTest#methodName          # single method
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # run locally; starts ../../docker-compose.yml via spring-boot-docker-compose
./mvnw compile jib:dockerBuild                        # build container image (use this for GKE — see warning below)
./mvnw spring-boot:build-image                        # local-only image build; broken on GKE ("too many symbolic links")
```

Swagger UI: `http://localhost:16543/swagger-ui.html` · OpenAPI JSON: `/v3/api-docs`.

## Architecture

Strict layered flow: **Controller → Service → JpaRepository → Entity**, with MapStruct mapping between entities and DTOs at the controller boundary.

- `controller/` — `@RestController` endpoints under `/rsus`. Document with springdoc `@Operation` / `@ApiResponses`. Never return JPA entities; always map to a DTO.
- `service/` — business logic. Annotate with `@Transactional(readOnly = true)` for reads, `@Transactional` for writes. Constructor injection via Lombok `@RequiredArgsConstructor` (no `@Autowired`).
- `repository/` — Spring Data `JpaRepository`. Use derived query methods (e.g. `findByRsuOptionTimDepositIsTrue`) or `@Query` JPQL — avoid hand-rolled SQL.
- `models/tables/` — JPA entities mirroring the **CV Manager Postgres schema owned by the parent project** (`resources/sql_scripts/CVManager_CreateTables.sql`). `spring.jpa.hibernate.ddl-auto=none` — this service does NOT manage schema or migrations; entity classes must stay in sync with that DDL.
- `models/dtos/` — request/response records or `@Data` classes. Map with MapStruct (`mapper/RsuDtoMapper`); `lombok-mapstruct-binding` is wired in `pom.xml` so Lombok and MapStruct annotation processors cooperate.
- `exception/GlobalExceptionHandler` — `@RestControllerAdvice` returning a consistent error payload. Add new handlers here rather than catching in controllers.

### Database notes

- PostGIS-backed: `Rsu.geography` is `org.locationtech.jts.geom.Point`; `hibernate-spatial` and `jts-core` are on the classpath. When adding spatial fields, use JTS types, not raw strings.
- Connects to the same Postgres instance used by the rest of `jpo-cvmanager` (Keycloak schema + CV Manager schema). Connection details come from env vars (`POSTGRES_SERVER_URL`, `POSTGRES_DB`, `PG_DB_USER`, `PG_DB_PASS`); `application.yaml` provides fallbacks for local dev.
- The `local` profile uses `spring-boot-docker-compose` integration to auto-start `../../docker-compose.yml` — the `cvmanager_postgres` service must be in `COMPOSE_PROFILES`.

## Testing

JUnit 5 + Spring Boot Test + **Testcontainers** for the database. Project policy (`.junie/guidelines.md`):

- **Do NOT use H2.** Always use Postgres via Testcontainers — the schema relies on PostGIS and the production dialect.
- **Do NOT mock the database or other infra.** Prefer real dependencies / test doubles. Mockito is fine for narrow unit tests of pure logic.
- Shared container wiring lives in `src/test/java/.../testutil/TestcontainersConfiguration.java`; reuse it in new integration tests.
- `src/test/java/.../testutil/repository/` contains test-only repositories used to seed reference data (Manufacturer, RsuModel, etc.) so integration tests can build a valid `Rsu` graph.
- Follow Given-When-Then; use `@Slf4j` + `log.debug` for test diagnostics, never `System.out`.

## Conventions specific to this service

- **Lombok everywhere on entities**: `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`. Prefer records for new DTOs.
- **MapStruct over hand-rolled mappers** — let the annotation processor generate impls; do not write `RsuDtoMapperImpl` by hand.
- **Read-only by default**: this service is a *bridge*, not a writer. New write endpoints require deliberate review; the schema is owned upstream.
- **Flyway is test-only.** `flyway-core` is a test-scoped dependency; the test context runs migrations from `resources/db/migration/` against the Testcontainers Postgres. The production service does not run Flyway — schema is owned by the parent project's migration pipeline (`resources/db/migration/`).
- Java version is pinned to **25** in `pom.xml` and `.java-version`. The CI matrix builds this module separately from the Java 22 intersection-api and Java 21 Keycloak provider.
