# Database Migrations

CV Manager uses [Flyway](https://flywaydb.org/) to manage PostgreSQL schema changes. Migrations run automatically as a Docker Compose service before the API starts.

**IMPORTANT**: Once a migration has been merged or applied to any shared environment, never rename it or modify its contents. Create a new migration instead.

## Directory layout

```
resources/db/
  migration/
    V1__baseline.sql   # Full current schema (all tables, indexes, sequences)
    V2__*.sql          # First post-baseline migration
    V{N}__*.sql        # Subsequent versioned migrations (sequential integers)
    R__sample_data.sql # Dev seed data -- re-runs when checksum changes
  flyway.toml               # Shared Flyway configuration
  README.md                 # This file
```

## Naming convention

```
V{N}__{snake_case_description}.sql
```

| Part          | Meaning                        | Example                   |
|---------------|--------------------------------|---------------------------|
| `N`           | Next integer in sequence       | `3`                       |
| `description` | Snake-case summary of change   | `add_rsu_telemetry_table` |

Full example: `V3__add_rsu_telemetry_table.sql`

**Why sequential integers?** Sequential integers enforce a known, unambiguous application order and keep migration history easy to scan. When two branches both add a migration, the version number conflict surfaces as a merge conflict that must be resolved explicitly. This forces team coordination on the correct ordering rather than silently allowing migrations to run out of order.

## Creating a new migration

1. Identify the next version number: check the highest `V{N}` in `resources/db/migration/` and increment by one.
2. Create a file: `resources/db/migration/V{N}__{snake_case_description}.sql`
3. Write forward-only DDL or DML. Flyway Community does not support rollbacks.
4. Write idempotent SQL where practical (`CREATE TABLE IF NOT EXISTS`, `ON CONFLICT DO NOTHING`).
5. Test locally before committing (see below).

## Running migrations locally

```bash
# Apply all pending migrations
docker compose run --rm flyway migrate

# Inspect current migration state
docker compose run --rm flyway info

# Validate checksums of applied migrations
docker compose run --rm flyway validate
```

The `flyway` service in `docker-compose.yml` runs automatically when you `docker compose up` — it completes before the API service starts.

## Adopting an existing database (baselineOnMigrate)

The Flyway config sets `baselineOnMigrate = true` and `baselineVersion = 1`. On first run against a database that already has the schema but no Flyway metadata table,
Flyway stamps V1 as applied without re-executing it, then applies any migrations with versions higher than 1. This is how existing non-production and production
environments are adopted without a rebuild.

## outOfOrder

`outOfOrder` is disabled. Migrations must be applied in strict version order. If two branches both introduce a migration with the same version number, the conflict surfaces as a merge conflict that must be resolved before either branch merges.

## Deprecated scripts

`resources/deprecated/sql_scripts/update_scripts/` contains the manually executed scripts that this Flyway setup replaces. That directory is kept as historical reference only. Do not add new scripts there.

## Schema Reference

Table descriptions are stored as SQL comments in the database (applied by migration `V2__add_table_comments.sql`) and are visible in psql via `\d+ <table>` or
`SELECT obj_description('public.<table>'::regclass)`. The table below summarizes each table for quick reference.

| Table                                   | Description                                                                                                 |
|-----------------------------------------|-------------------------------------------------------------------------------------------------------------|
| `manufacturers`                         | RSU and OBU manufacturers supported by this deployment. Tested: Commsignia, Kapsch, Yunex.                  |
| `rsu_models`                            | RSU hardware models. Linked to a manufacturer; used for display and firmware upgrade identification.        |
| `firmware_images`                       | Known RSU firmware packages. Stores retrieval and install information used by the API.                      |
| `firmware_upgrade_rules`                | Valid firmware upgrade paths. A from_id->to_id row authorizes a direct upgrade; no row blocks it.           |
| `rsu_credentials`                       | SSH credentials for RSU remote access. Referenced by nickname only — never transmitted over the network.    |
| `snmp_credentials`                      | SNMP credentials for message forwarding configuration. Referenced by nickname only.                         |
| `snmp_protocols`                        | SNMP protocol versions used by RSUs. Referenced by nickname.                                                |
| `rsus`                                  | All RSUs in this deployment. Each row appears on the CV Manager map. `primary_route` is denormalized here.  |
| `rsu_options`                           | Per-RSU feature flags: `tim_deposit` and `snmp_monitoring`.                                                 |
| `ping`                                  | RSU online/offline ping results. Keep to last 24 hours per RSU — a large table degrades map load times.     |
| `rsu_health`                            | RSU health records from SNMP monitoring. Keep recent data only (same guidance as `ping`).                   |
| `scms_health`                           | ISS SCMS certificate health per RSU. Polled every 6 hours. Requires an ISS SCMS service agreement.          |
| `iss_keys`                              | ISS SCMS API tokens used by `iss_health_check` to query certificate status.                                 |
| `roles`                                 | User roles. Required rows: `admin`, `operator`, `user`.                                                     |
| `users`                                 | Authorized CV Manager users. `keycloak_id` links to Keycloak. `super_user=1` grants cross-org admin access. |
| `organizations`                         | Deployment organizations. Users and RSUs are scoped to organizations.                                       |
| `user_organization`                     | Many-to-many user-to-organization assignments with a role per membership.                                   |
| `rsu_organization`                      | Many-to-many RSU-to-organization assignments.                                                               |
| `snmp_msgfwd_type`                      | Lookup table for SNMP message forwarding types (e.g., RX, TX).                                              |
| `snmp_msgfwd_config`                    | Active SNMP message forwarding rules per RSU (type, destination IP/port, time window).                      |
| `email_type`                            | Lookup table for notification email categories.                                                             |
| `user_email_notification`               | User subscriptions to notification email types, including frequency settings.                               |
| `obu_ota_requests`                      | Over-the-air firmware update requests for OBU devices.                                                      |
| `intersections`                         | Managed signalized intersections used by intersection management features.                                  |
| `intersection_organization`             | Many-to-many intersection-to-organization assignments.                                                      |
| `rsu_intersection`                      | Association between RSUs and nearby intersections.                                                          |
| `consecutive_firmware_upgrade_failures` | Consecutive firmware upgrade failure counts per RSU, used to enforce retry limits.                          |
| `max_retry_limit_reached_instances`     | Records when an RSU hits the maximum consecutive firmware upgrade failure limit.                            |

## Kubernetes deployment

The Flyway image for Kubernetes is built and pushed to GHCR automatically by CI on every
merge to `develop` or `cdot-release*`. See `resources/kubernetes/README.md` for how to
identify the correct image tag and apply the migration Job.

**Views**

| View                    | Description                                                       |
|-------------------------|-------------------------------------------------------------------|
| `rsu_organization_name` | Joins `rsu_organization` with organization names for display use. |

### Critical data requirements

- **`roles`** must always contain exactly three rows with names `'admin'`, `'operator'`, and `'user'`. The application depends on these exact strings for permission checks.
- **`ping`** and **`rsu_health`** should be pruned regularly. Retaining more than 24 hours of data per RSU causes noticeable slowdowns when loading the map.
- **`scms_health`** data is only populated if you have an active ISS SCMS API service agreement.

## Schema Integrity Review (2026-05)

A schema constraint and index review was completed in May 2026. Changes are documented directly in the migration files that applied them — read those files for full rationale. Inline `--` comments and `COMMENT ON INDEX` statements explain each decision at the point it was made.

| Migration | Summary |
|---|---|
| `V3__schema_constraint_integrity.sql` | UNIQUE on `users.keycloak_id`; UNIQUE constraints on `rsu_organization`, `user_organization`, `intersection_organization` (with duplicate row cleanup); `ON DELETE CASCADE` on `rsu_options`, `consecutive_firmware_upgrade_failures`, and `user_organization(user_id)` |
| `V4__schema_index_optimization.sql` | Dropped 9 redundant or unused indexes; added composite `(rsu_id, timestamp DESC)` indexes on `ping`, `rsu_health`, and `scms_health` |
| `V5__remaining_relational_constraints.sql` | UNIQUE on `firmware_upgrade_rules(from_id, to_id)` (with duplicate cleanup); `ON DELETE CASCADE` on both FKs of `rsu_intersection`, `rsu_organization`, `intersection_organization`, on `snmp_msgfwd_config(rsu_id)`, `max_retry_limit_reached_instances(rsu_id)`, and on `user_organization(organization_id)`. `user_organization(role_id)` is left RESTRICT on purpose (roles are reference data and must not be deletable while assigned) |
| `V6__service_pattern_indexes.sql` | Added 6 indexes derived from active repository query patterns: FK-column indexes (`user_organization.organization_id`, `user_email_notification.email_type_id`, `firmware_upgrade_rules.from_id`, `rsu_intersection.intersection_id`), `rsus.primary_route`, and a partial index `rsu_options(rsu_id) WHERE tim_deposit = true` |

> **Entity annotations are documentation only.** This service does not set
> `spring.jpa.hibernate.ddl-auto`, so Hibernate generates and updates no DDL — Flyway owns the
> schema. The `@Column(unique = ...)`, `@UniqueConstraint`, and `@OnDelete` annotations on the
> JPA entities under `models/postgres/tables/` therefore have no runtime effect: they describe the
> schema for readers and must be kept in sync with these migrations by hand. In particular,
> `@OnDelete` does **not** change Hibernate's in-session delete behavior — the cascade behavior is
> entirely the database FK actions defined above. If `ddl-auto` is ever set to `validate`, note that
> it checks tables and columns but not FK actions or unique constraints, so drift would not be caught.

> **Already applied these migrations locally before this change?** Three of these scripts were
> edited after their first authoring (removing redundant `BEGIN/COMMIT`, adding `DROP CONSTRAINT
> IF EXISTS`, and the `user_organization(organization_id)` cascade). They were never merged or applied
> to a shared environment, so editing them is allowed — but a local database that already ran the old
> versions will fail checksum validation. Run `docker compose run --rm flyway repair` (then `migrate`)
> or recreate your local schema.

**Deferred items:**

- `obu_ota_requests.obu_firmware_version` and `requested_firmware_version` are raw strings with no FK to `firmware_images`. This is intentional: OBU firmware is sourced from blob storage and is not represented in `firmware_images`. A FK would require a separate OBU firmware catalog table.
- Keycloak schema integrity review is deferred to a separate effort.
