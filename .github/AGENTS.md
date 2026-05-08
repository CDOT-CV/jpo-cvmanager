# JPO CV Manager — Agent Instructions

**Project**: USDOT JPO Connected Vehicle Manager — a web app for managing RSUs and OBUs, with a React/TypeScript frontend, Python Flask API, Java Spring Boot intersection API, and Docker-based infrastructure.

## Repository Structure

| Path                         | Description                                              |
| ---------------------------- | -------------------------------------------------------- |
| `webapp/`                    | React + TypeScript + Redux Toolkit frontend              |
| `services/api/`              | Python Flask-RESTful API                                 |
| `services/intersection-api/` | Java Spring Boot API (Kafka, MongoDB)                    |
| `services/common/`           | Shared Python utilities (pgquery, auth, env)             |
| `services/addons/`           | Background services (RSU status, OTA server, ISS health) |
| `jpo-utils/`                 | Git submodule — Kafka/MongoDB/monitoring Docker configs  |
| `resources/`                 | Keycloak realm, Kubernetes manifests, SQL scripts        |
| `docs/`                      | Developer docs and PR guidelines                         |

## Build & Test Commands

### Webapp (frontend)

```sh
cd webapp
npm ci               # install dependencies
npm test             # typecheck + vitest
npm run typecheck    # tsc --noEmit only
```

### Python services

```sh
cd services
python -m pip install -r requirements.txt
python -m pytest -v  # uses pytest.ini; sets PYTHONPATH for all sub-services
```

### Java services

```sh
cd services/intersection-api/api
mvn clean install
mvn test
```

### Full stack (Docker)

```sh
cp sample.env .env   # then fill required vars
git submodule update --init --recursive
docker compose up -d
```

## Key Conventions

### Webapp (React / TypeScript)

- See the [jpo-cvmanager-webapp skill](.github/skills/jpo-cvmanager-webapp/SKILL.md) for comprehensive frontend patterns.
- **Feature folders** (`src/features/<name>/`): co-locate the component, Redux slice, and tests.
- **RTK Query API slices** go in `src/features/api/<name>ApiSlice.ts`. All tag constants are `UPPER_SNAKE_CASE as const`.
- **Shared Redux state** (cross-feature) goes in `src/generalSlices/`.
- **TypeScript models** go in `src/models/*.d.ts` — no logic, types only.
- **MUI** (`@mui/material`) is the UI library. Custom theme tokens are in `src/styles/index.ts`.
- **Feature flags**: use `evaluateFeatureFlags(tag)` from `src/feature-flags.tsx`; flag tags map to env vars in `EnvironmentVars.tsx`.
- Tests use **Vitest + React Testing Library**. Use `replaceChaoticIds` from `src/utils/test-utils.tsx` in snapshot tests.

### Python API (`services/api/`)

- Flask-RESTful resources registered in `services/api/src/main.py`.
- **Always use parameterized queries** via `pgquery.query_db(query, params=dict)`. Never interpolate user data into SQL strings.
- Auth middleware (`middleware.py`) validates Keycloak JWTs and populates `request.environ` with `EnvironWithOrg`/`EnvironWithoutOrg`.
- Use `@require_permission` decorator from `common/auth_tools.py` for endpoint authorization.
- Environment variables loaded via `common_environment.get_env_var()` — use `error=True` for required vars.
- Feature flags (`ENABLE_RSU_FEATURES`, `ENABLE_INTERSECTION_FEATURES`, `ENABLE_WZDX_FEATURES`) gate endpoint registration in `main.py`.

### Intersection API (`services/intersection-api/`)

- Java 22, Spring Boot 3.x, Maven build.
- Endpoint resources under `us.dot.its.jpo.ode.api`.
- Run tests: `cd services/intersection-api/api && mvn test`

### General

- Default branch is `develop`. Target `develop` for all PRs.
- All PRs are **squash merged**. Keep PRs under ~400 LOC changed.
- PR description template: [docs/pull_request_template.md](docs/pull_request_template.md).
- Developer workflow and fork synchronization: [docs/developer_best_practices.md](docs/developer_best_practices.md).

## Important Files

- `webapp/src/store.tsx` — Redux store; register new slices/RTK APIs here.
- `webapp/src/EnvironmentVars.tsx` — frontend env-var constants and feature flag sources.
- `services/api/src/main.py` — Flask app entry point; register new endpoints here.
- `services/common/pgquery.py` — PostgreSQL connection pool and `query_db`.
- `services/common/auth_tools.py` — `require_permission`, `UserInfo`, `ORG_ROLE_LITERAL`.
- `sample.env` / `sample-full.env` — reference for all environment variables.
