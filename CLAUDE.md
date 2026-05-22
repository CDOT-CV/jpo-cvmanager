# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JPO CV Manager — a USDOT web application for managing Connected Vehicle devices (RSUs and OBUs) through a Mapbox-based UI. Multi-organization, role-based access (Admin/Operator/User) with Keycloak authentication.

## Architecture

The system is a multi-service Docker Compose application with four main codebases:

### 1. Webapp (`webapp/`) — React 17 + TypeScript + Vite
- **State management**: Redux Toolkit with slices in `src/generalSlices/` (global: rsu, user, config, intersection, wzdx, timeSync, haas) and `src/features/` (per-feature slices for admin CRUD operations)
- **RTK Query API slices**: `src/features/api/` — intersectionApiSlice, organizationApiSlice, rsuCountsApiSlice, rsuApiSlice
- **Pages**: `src/pages/` — Map, Admin, IntersectionDashboard, IntersectionMapView
- **API clients**: `src/apis/` — rsu-api.ts (CV Manager API), auth-api.ts (Keycloak), intersections/ (intersection API)
- **Feature flags**: `src/feature-flags.tsx` — route guards and conditional rendering based on `EnvironmentVars` (ENABLE_RSU_FEATURES, ENABLE_INTERSECTION_FEATURES, ENABLE_WZDX_FEATURES, ENABLE_HAAS_FEATURES)
- **Environment config**: `src/EnvironmentVars.tsx` — reads `process.env` vars injected by Vite's `envPlugin()` in `vite.config.ts`
- **UI**: MUI v6 with customizable theming (`src/styles/`)

### 2. CV Manager API (`services/api/`) — Python 3.12.2, Flask + flask_restful
- Entry point: `services/api/src/main.py` — registers REST resources, conditionally enables endpoints based on feature flags
- **Middleware** (`services/api/src/middleware.py`): WSGI middleware handling auth (Keycloak token introspection), organization-scoped access, and feature flag enforcement
- **Auth/permissions** (`services/common/auth_tools.py`): `UserInfo` class, auth context hierarchy (`EnvironNoAuth` → `EnvironWithoutOrg` → `EnvironWithOrg`), `@require_permission` decorator, `DefaultPermissionChecker` with role hierarchy (User < Operator < Admin), organization-scoped resource checks
- **Database**: PostgreSQL via SQLAlchemy + pg8000 (`services/common/pgquery.py`), MongoDB via pymongo for BSM/PSM/SSM/SRM data
- **Shared code**: `services/common/` — pgquery, auth_tools, email_util, SNMP utilities

### 3. Intersection API (`services/intersection-api/api/`) — Java 22, Spring Boot + Maven
- Connects to MongoDB (conflict monitor data) and Kafka for live intersection data (MAP, SPaT, BSM)
- Requires GitHub token for Maven package resolution (`~/.m2/settings.xml`)
- Swagger UI at `http://localhost:8088/swagger-ui/index.html`
- Uses TestContainers for integration tests (Docker Engine must be running)

### 4. RSU Info Bridge (`services/rsu-info-bridge/`) — Java 25, Spring Boot
- Package: `com.trihydro.rsuinfobridge` (controller, service, models)
- Uses Maven wrapper (`./mvnw`), Jib plugin for Docker builds
- Swagger UI at `http://localhost:<port>/swagger-ui.html`

### 5. Keycloak Custom Provider (`resources/keycloak/`) — Java 21
- Custom Keycloak user storage provider for CV Manager user integration

### Add-on Services (`services/addons/images/`)
- `count_metric` — Kafka-based V2X message counting per RSU
- `firmware_manager` — automated RSU firmware upgrades
- `iss_health_check` — ISS SCMS certificate status (GCP dependency)
- `rsu_status_check` — RSU online status (Zabbix or direct ping)
- `obu_ota_server` — OBU over-the-air updates

### Database
- PostgreSQL 15 with PostGIS: schema in `resources/sql_scripts/CVManager_CreateTables.sql`, sample data in `CVManager_SampleData.sql`, migrations in `update_scripts/`
- Keycloak shares the same Postgres instance (schema: `keycloak`)

### Submodule
- `jpo-utils/` — shared Docker infrastructure (Kafka, MongoDB, Grafana dashboards)

## Build & Run Commands

### Full stack (Docker)
```sh
git submodule update --init --recursive
cp sample.env .env  # then fill in DOCKER_HOST_IP, MAPBOX_TOKEN, MAVEN_GITHUB_TOKEN
docker compose up -d
# Profiles available: basic, webapp, intersection, intersection_no_api, mongo_full, kafka_full, conflictmonitor, addons, obu_ota, kafka_connect_standalone
```
Default login: `test@gmail.com` / `tester` at `http://localhost:3000`

### Webapp
```sh
cd webapp
npm ci
npm run dev          # Vite dev server on port 3000
npm test             # Vitest (watch mode)
npm test -- --run    # Single run
npm run test:coverage  # Coverage report
npm run build        # Production build
npm run lint         # ESLint
npm run typecheck    # TypeScript check
```

### Python services (API + addons)
```sh
cd services
pip install -r requirements.txt
python -m pytest                           # All Python tests
python -m pytest api/tests/                # API tests only
python -m pytest addons/tests/             # Addon tests only
python -m pytest path/to/test_file.py      # Single test file
python -m pytest -k "test_name"            # Single test by name
python -m coverage run -m pytest && python -m coverage xml  # Coverage
```
pytest.ini configures pythonpath for all service modules and sets `asyncio_mode=auto`.

### Intersection API (Java 22)
```sh
cd services/intersection-api/api
mvn clean install                                  # Build (Docker Engine required for TestContainers)
mvn test                                           # Run tests
mvn clean verify                                   # Tests + JaCoCo coverage
mvn -Dspring-boot:run.profiles=dev spring-boot:run  # Run locally (needs application-dev.yaml)
```

### RSU Info Bridge (Java 25)
```sh
cd services/rsu-info-bridge
./mvnw clean verify              # Build + tests
./mvnw spring-boot:run           # Run locally
```

### Keycloak Custom Provider (Java 21)
```sh
cd resources/keycloak
mvn clean install                # Build custom user provider
```

## CI

GitHub Actions (`.github/workflows/ci.yml`) runs on PR to any branch and push to `develop`/`cdot-release*`:
- `build_api` — Python 3.12.2 pytest + coverage
- `webapp` — Node 22, `npm ci && npm test -- --coverage`
- `build_intersection_api` — Java 22, `mvn verify`
- `build_rsu_info_bridge` — Java 25, `./mvnw clean verify`
- `build_keycloak_provider` — Java 21, Maven build of custom Keycloak user provider
- `validate_migrations` — runs Flyway against a live Postgres 15 container, validates all migration SQL
- `build_flyway_image` — builds and pushes `cvmanager-flyway` to GHCR; runs only after `validate_migrations` succeeds; pushes only on `push` events (not PRs)
- SonarCloud analysis (upstream only)
- PR change limit check (configurable via `MAX_CHANGED_FILES` repo var)

## Key Conventions

- **Feature flags** control both UI routes and API endpoints. When adding a new feature area, register it in: `feature-flags.tsx` (webapp), `middleware.py:feature_tags` (API), and `main.py` resource registration.
- **Organization-scoped access**: API middleware injects auth context into the WSGI environ. Three context types: `EnvironNoAuth` (no auth required), `EnvironWithoutOrg` (authenticated, no org — super_user operations), `EnvironWithOrg` (authenticated with org — most endpoints). Endpoint handlers access via `request.environ["user"]`. Use `@require_permission` decorator for authorization.
- **Roles**: User < Operator < Admin, plus `super_user` flag for cross-org access.
- **SQL queries**: Use parameterized queries via SQLAlchemy text bindings (`:param_name`). Use `generate_sql_placeholders_for_list()` for IN clauses.
- **Docker profiles**: Services are grouped by profile (basic, webapp, intersection, etc.). Set `COMPOSE_PROFILES` in `.env`.
- **PR process**: Squash merge to `develop`. Keep PRs under 400 lines changed (1000 max). Use the PR template in `docs/pull_request_template.md`.
- **Upstream fork**: This is a CDOT fork of `usdot-jpo-ode/jpo-cvmanager`. See `docs/developer_best_practices.md` for fork sync procedures.
