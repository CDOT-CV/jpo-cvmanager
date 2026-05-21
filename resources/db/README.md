# Database Migrations

CV Manager uses [Flyway](https://flywaydb.org/) to manage PostgreSQL schema changes. Migrations run automatically as a Docker Compose service before the API starts.

## Directory layout

```
resources/db/
  migration/
    V1__baseline.sql        # Full current schema (all tables, indexes, sequences)
    R__sample_data.sql      # Dev seed data — re-runs when checksum changes
    V20260521001__*.sql     # Future versioned migrations (date-stamped)
  flyway.toml               # Shared Flyway configuration
  README.md                 # This file
```

## Naming convention

```
V{YYYYMMDDNNN}__{snake_case_description}.sql
```

| Part | Meaning | Example |
|------|---------|---------|
| `YYYY` | Year | `2026` |
| `MM` | Month | `05` |
| `DD` | Day | `21` |
| `NNN` | Daily sequence (001–999) | `001` |
| `description` | Snake-case summary | `add_rsu_telemetry_table` |

Full example: `V20260521001__add_rsu_telemetry_table.sql`

**Why timestamp-based?** This project is a CDOT fork of the upstream `usdot-jpo-ode/jpo-cvmanager`. Timestamp prefixes prevent version collisions when syncing changes between the fork and upstream, which may introduce integer-versioned migrations at any point.

## Creating a new migration

1. Create a file: `resources/db/migration/V{YYYYMMDDNNN}__{description}.sql`
2. Write forward-only DDL or DML. Flyway Community does not support rollbacks.
3. Write idempotent SQL where practical (`CREATE TABLE IF NOT EXISTS`, `ON CONFLICT DO NOTHING`).
4. Test locally before committing (see below).

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

The Flyway config sets `baselineOnMigrate = true` and `baselineVersion = 1`. On first run against a database that already has the schema but no Flyway metadata table, Flyway stamps V1 as applied without re-executing it, then applies any migrations with versions higher than 1. This is how existing non-production and production environments are adopted without a rebuild.

## outOfOrder

`outOfOrder = true` allows a migration with a lower version number than the current HEAD to be applied. This is expected when a change developed on a branch has a timestamp from before the branch's merge date. Flyway will apply it in order relative to other pending migrations.

## Deprecated scripts

`resources/sql_scripts/update_scripts/` contains the manually-executed scripts that this Flyway setup replaces. That directory is kept as historical reference only. Do not add new scripts there.
