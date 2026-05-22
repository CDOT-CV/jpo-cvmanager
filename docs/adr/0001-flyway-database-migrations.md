# ADR-0001: Flyway for Automated Database Migrations

**Date**: 2026-05-22  
**Status**: Accepted  
**Deciders**: CDOT CV Platform team  

---

## Context

Database schema changes in jpo-cvmanager were managed through a directory of manually created and manually executed SQL scripts (`resources/sql_scripts/update_scripts/`). This approach had several compounding problems:

- **No enforced ordering.** Scripts had to be applied in the correct sequence by a human; there was no mechanism to detect or prevent out-of-order execution.
- **No idempotency guarantees.** A script applied twice would fail or corrupt data in most cases. There was no record of which scripts had been applied to which environment.
- **Inconsistent environments.** Dev, test, and production databases could diverge silently when a migration was missed. Diagnosing schema drift required manual comparison.
- **No pipeline integration.** Migrations were not part of CI or the Kubernetes deployment. Applying changes required direct database access and manual steps on every environment.

The work item called for: version-controlled, repeatable, automated migrations; pipeline integration; at least one non-production environment validation; a standard naming convention; and developer documentation.

---

## Decision

We adopt **Flyway 10 Community Edition** as the database migration tool for jpo-cvmanager.

### What Flyway does

Flyway tracks which migration scripts have been applied to a database in a `flyway_schema_history` table. On each run, it identifies unapplied scripts, applies them in version order, and records the result. If a script fails, further scripts do not run and the job exits with a non-zero code.

### Key configuration decisions

#### Timestamp versioning (`V{YYYYMMDDHHmm}__description.sql`)

We use UTC timestamp prefixes rather than sequential integers (`V1__`, `V2__`, etc.). Sequential integers collide when multiple developers introduce migrations on parallel branches and both are merged — the second merge arrives with a version number already claimed. Timestamps are unique by construction and require no coordination across branches or contributors.

The baseline is `V1__baseline.sql` as a special case — it consolidates the entire pre-Flyway schema history into a single known starting point.

#### `baselineOnMigrate = true`

Existing production databases already have the schema from years of manual scripts. We cannot re-run `V1__baseline.sql` on them. `baselineOnMigrate` tells Flyway to stamp the baseline version as already applied on first run, then apply only newer migrations. This allows safe adoption without dropping and recreating the schema.

#### `outOfOrder = true`

Feature branches may produce migration files with timestamps that predate the branch merge date. `outOfOrder` allows these to be applied even if higher-versioned migrations have already run, which is the expected pattern for parallel feature branch development.

#### Repeatable migrations (`R__sample_data.sql`)

Dev-environment seed data is stored in a repeatable migration (`R__` prefix). Flyway re-applies it whenever its checksum changes. The production Docker image excludes all `R__` files via a `COPY migration/V*.sql` glob in the Dockerfile, so  seed data never reaches production.

### Deployment integration

#### Docker image

A dedicated Docker image (`cvmanager-flyway`) is built from `flyway/flyway:10-alpine` with the versioned migration scripts and `flyway.toml` baked in. The image is published to GHCR on every merge to `develop` or a `cdot-release*` branch. CI gates publication on a passing `validate_migrations` job, which runs Flyway against a live PostgreSQL 15 container.

#### Kubernetes

A Kubernetes `Job` (`cv-manager-flyway.yaml`) runs the migration image before the API starts. Deployment order is:

1. Apply Postgres and the Flyway Job.
2. Wait: `kubectl wait --for=condition=complete job/cv-manager-flyway-migrate --timeout=120s`
3. Apply the API Deployment. An init container in the API pod polls `flyway_schema_history` and blocks startup until at least one successful migration row exists.

#### Local development

A `flyway` service in `docker-compose.yml` runs automatically with the `basic` profile, mounting local migration files and applying them against the dev Postgres container before the API starts.

#### rsu-info-bridge

The RSU Info Bridge service (Java/Spring Boot) is a read-only consumer of the CV Manager schema. It does not own or run migrations in production. For integration tests, Flyway is included as a `test`-scoped dependency: a `TestcontainersConfiguration` bean wires Flyway explicitly (Spring Boot 4 removed `FlywayAutoConfiguration`) and runs the parent project's migration scripts against a Testcontainers PostGIS instance via Maven `testResources`.

---

## Alternatives Considered

### Liquibase

Liquibase is the other widely adopted migration tool in the Java ecosystem. It supports XML, YAML, JSON, and SQL changelogs, and offers more granular rollback support. We chose Flyway over Liquibase for these reasons:

- **Simpler mental model.** Flyway's versioned SQL files map directly to how the team was already thinking about migrations. No XML/YAML wrapper is needed.
- **Lighter tooling.** The Flyway Community Docker image is smaller and has no licensing considerations at our scale.
- **Sufficient rollback story.** Neither tool provides automatic DDL rollback on PostgreSQL (transactions around DDL are non-trivial). In practice, rollback means writing a new forward migration, which Flyway handles fine.

### Alembic (Python)

The CV Manager API is Python-based. Alembic (SQLAlchemy migrations) was considered to keep migrations in the same language/stack. We rejected this approach:

- Alembic couples migration execution to the Python service runtime, which is unsuitable for the rsu-info-bridge (Java) and any future services that share the same schema.
- A dedicated migration image that runs as a standalone Kubernetes Job is a cleaner separation of concerns: migrations are not entangled with application startup.
- Flyway's SQL-only approach is language-agnostic and readable by all contributors regardless of their primary language.

### Continued manual scripts

Continuing with manual SQL scripts was rejected. The status quo provided no ordering enforcement, no history tracking, and no pipeline integration. The risk of environment drift and missed migrations was the primary driver for this work item.

---

## Consequences

### Positive

- **Consistent schema across environments.** Flyway tracks applied migrations and enforces ordering, eliminating the category of bugs caused by missed or out-of-order scripts.
- **Pipeline-gated changes.** Every PR runs `validate_migrations` against a live Postgres container. Migration SQL errors are caught before merge.
- **Auditable history.** The `flyway_schema_history` table provides a timestamped record of every migration applied to every environment.
- **No manual production access required.** The Kubernetes Job applies migrations as part of the normal deployment workflow.

### Negative / Trade-offs

- **No automatic rollback.** Flyway Community does not generate rollback scripts. Reverting a migration requires writing a new forward migration. This is a known limitation of SQL-level DDL migration tools on PostgreSQL.
- **outOfOrder is a code smell at scale.** Allowing out-of-order migrations is necessary for parallel branch development but can obscure migration history if overused. Developers should still aim to merge migration branches promptly.
- **Baseline version complexity.** New developers must understand `baselineOnMigrate` when standing up a fresh database against an existing environment. This is documented in `resources/db/README.md` but adds onboarding friction.
- **Flyway version pinning.** The project is pinned to Flyway 10. Future major versions may require migration script or configuration changes. The `validate_migrations` CI job provides a safety net for detecting breakage.

---

## References

- Migration scripts: `resources/db/migration/`
- Flyway configuration: `resources/db/flyway.toml`
- Docker image: `resources/db/Dockerfile`
- Developer guide: `resources/db/README.md`
- Kubernetes Job: `resources/kubernetes/cv-manager-flyway.yaml`
- Kubernetes deployment guide: `resources/kubernetes/README.md`
- CI jobs: `.github/workflows/ci.yml` (`validate_migrations`, `build_flyway_image`)
