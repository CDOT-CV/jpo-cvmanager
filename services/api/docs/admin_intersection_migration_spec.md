# AdminIntersection Migration Specification

This document describes the behavior of the `AdminIntersection` REST resource at `/admin-intersection`. It covers what the API does, how authorization works, the data models involved, and the exact behavior of each endpoint. Use this document to guide re-implementation in a different framework.

## Table of Contents

- [Overview](#overview)
- [Feature Flag](#feature-flag)
- [Database Tables](#database-tables)
- [Authorization](#authorization)
- [CORS](#cors)
- [Endpoints](#endpoints)
  - [OPTIONS](#options)
  - [GET](#get-admin-intersection)
  - [PATCH](#patch-admin-intersection)
  - [DELETE](#delete-admin-intersection)
- [Allowed Selections](#allowed-selections)
- [Known Issues to Fix](#known-issues-to-fix)

---

## Overview

This API manages traffic intersections in the CV Manager system. An intersection has a numeric ID, a geographic reference point, an optional bounding box, an optional name, an optional origin IP address, and relationships to organizations and RSUs (Roadside Units).

The API supports four operations:

| Method  | Role Required | What It Does                                   |
|---------|---------------|------------------------------------------------|
| OPTIONS | None          | Returns CORS preflight headers                 |
| GET     | USER          | Reads one intersection or all intersections    |
| PATCH   | OPERATOR      | Updates an intersection and its relationships  |
| DELETE  | OPERATOR      | Removes an intersection and its relationships  |

---

## Feature Flag

This resource is only registered when `ENABLE_INTERSECTION_FEATURES` is `True`. When the flag is off, the `/admin-intersection` route does not exist.

---

## Database Tables

Three tables are directly involved. The API reads from two additional lookup tables (`organizations` and `rsus`) but never writes to them.

```mermaid
erDiagram
    intersections {
        SERIAL intersection_id PK
        VARCHAR intersection_number UK "NOT NULL"
        GEOGRAPHY ref_pt "POINT, SRID 4326, NOT NULL"
        GEOGRAPHY bbox "POLYGON, SRID 4326, nullable"
        VARCHAR intersection_name "nullable"
        INET origin_ip "nullable"
    }

    intersection_organization {
        SERIAL intersection_organization_id PK
        INTEGER intersection_id FK
        INTEGER organization_id FK
    }

    rsu_intersection {
        SERIAL rsu_intersection_id PK
        INTEGER rsu_id FK
        INTEGER intersection_id FK
    }

    organizations {
        SERIAL organization_id PK
        VARCHAR name UK
    }

    rsus {
        SERIAL rsu_id PK
        INET ipv4_address UK
    }

    intersections ||--o{ intersection_organization : "has"
    intersections ||--o{ rsu_intersection : "has"
    organizations ||--o{ intersection_organization : "belongs to"
    rsus ||--o{ rsu_intersection : "belongs to"
```

Foreign keys use `ON DELETE NO ACTION`. The application must delete relationship rows before deleting the intersection itself.

---

## Authorization

### User Context

Authentication is handled by WSGI middleware that runs before any endpoint code. The middleware reads a Keycloak token from the `Authorization` header, introspects it, and places a user context object into the WSGI environment. Endpoints access this as `request.environ["user"]`.

The user context contains:

| Field              | Type                          | Description                                           |
|--------------------|-------------------------------|-------------------------------------------------------|
| `user_info.email`  | `str`                         | The user's email address                              |
| `user_info.super_user` | `bool`                   | Whether the user has cross-organization access        |
| `user_info.organizations` | `dict[str, role]`       | Map of organization name to the user's role in it     |
| `organization`     | `str` or `None`               | Organization scoped by the `Organization` request header |
| `role`             | `role` or `None`              | The user's role in the scoped organization            |

### Role Hierarchy

```
USER < OPERATOR < ADMIN
```

A user with OPERATOR role satisfies a USER requirement. A user with ADMIN role satisfies both.

### Permission Checking

All endpoints (except OPTIONS) use the `@require_permission` decorator. The decorator:

1. Verifies the user is authenticated (has `user_info`). Returns **401** if not.
2. Checks the user's role meets the required threshold. Returns **403** if not.
3. Optionally checks the user has access to a specific resource (by type and ID). Returns **403** if not.
4. Produces a `qualified_orgs` list: the organizations where the user holds the required role or higher.
5. Superusers always pass role checks but still receive a `qualified_orgs` list based on their actual org memberships.

### Double Permission Checking on PATCH and DELETE

PATCH and DELETE use two layers of permission checks (defense-in-depth):

1. **Outer check** (on the HTTP method handler): Verifies the user has OPERATOR role. No resource-type check.
2. **Inner check** (on the business logic function): Verifies the user has OPERATOR role AND has access to the specific intersection via the INTERSECTION resource type.

Both checks must pass. The new implementation must preserve this two-layer pattern.

### Organization Restriction Enforcement (PATCH only)

After permission checks pass, PATCH enforces that the user is allowed to modify the specific organizations listed in `organizations_to_add` and `organizations_to_remove`. For non-superusers, each organization in those lists must appear in the user's `qualified_orgs`. If any organization is not in the user's qualified list, the request is rejected with **403**.

Superusers skip this check.

---

## CORS

### Preflight Headers (OPTIONS response)

| Header                           | Value                                     |
|----------------------------------|-------------------------------------------|
| `Access-Control-Allow-Origin`    | Value of `CORS_DOMAIN` environment variable |
| `Access-Control-Allow-Headers`   | `Content-Type,Authorization,Organization` |
| `Access-Control-Allow-Methods`   | `GET,PATCH,DELETE`                        |
| `Access-Control-Max-Age`         | `3600`                                    |

### Standard Response Headers (all other responses)

| Header                          | Value                                      |
|---------------------------------|--------------------------------------------|
| `Access-Control-Allow-Origin`   | Value of `CORS_DOMAIN` environment variable |
| `Content-Type`                  | `application/json`                         |

---

## Endpoints

### OPTIONS

Returns `204 No Content` with CORS preflight headers. No authentication required.

---

### GET /admin-intersection

Retrieves intersection data. Can return a single intersection or all intersections.

#### Query Parameters

| Parameter         | Type   | Required | Values                                      |
|-------------------|--------|----------|---------------------------------------------|
| `intersection_id` | string | Yes      | `"all"` for all intersections, or a specific intersection number |

#### Authorization

Requires USER role.

#### Organization Filtering

The results are filtered based on who is asking:

| User Type                         | What They See                            |
|-----------------------------------|------------------------------------------|
| Has a scoped organization         | Only intersections belonging to that org |
| No scoped org, is a superuser     | All intersections (no filter)            |
| No scoped org, not a superuser    | Intersections belonging to any of their qualified orgs |

#### Row Aggregation

The database query joins intersections with organizations and RSUs, which produces multiple rows when an intersection belongs to multiple orgs or has multiple RSUs. The API aggregates these into a single object per intersection:

- Groups rows by `intersection_number`
- Collects distinct `org_name` values into an `organizations` list
- Collects distinct non-null `rsu_ip` values into an `rsus` list

#### Response Shape

The response shape depends on what was requested and what was found:

**Single intersection, found** — returns `intersection_data` as an object, plus `allowed_selections`:

```json
{
  "intersection_data": {
    "intersection_id": "1123",
    "ref_pt": { "latitude": 40.1, "longitude": -105.1 },
    "bbox": { "latitude1": 40.0, "longitude1": -105.2, "latitude2": 40.2, "longitude2": -105.0 },
    "intersection_name": "Main St & 1st Ave",
    "origin_ip": "10.0.0.1",
    "organizations": ["Org A"],
    "rsus": ["192.168.1.1"]
  },
  "allowed_selections": {
    "organizations": ["Org A", "Org B"],
    "rsus": ["192.168.1.1", "192.168.1.2"]
  }
}
```

**Single intersection, not found** — returns empty `intersection_data` object, plus `allowed_selections`:

```json
{
  "intersection_data": {},
  "allowed_selections": {
    "organizations": ["Org A"],
    "rsus": ["192.168.1.1"]
  }
}
```

**All intersections** — returns `intersection_data` as a list (may be empty). No `allowed_selections`:

```json
{
  "intersection_data": [
    {
      "intersection_id": "1123",
      "ref_pt": { "latitude": 40.1, "longitude": -105.1 },
      "bbox": { "latitude1": 40.0, "longitude1": -105.2, "latitude2": 40.2, "longitude2": -105.0 },
      "intersection_name": "Main St & 1st Ave",
      "origin_ip": "10.0.0.1",
      "organizations": ["Org A"],
      "rsus": ["192.168.1.1"]
    }
  ]
}
```

#### Data Flow

```mermaid
flowchart TD
    A[/"GET /admin-intersection?intersection_id=..."/]
    A --> B{intersection_id present?}
    B -->|No| C[/"400 Bad Request"/]
    B -->|Yes| D{intersection_id != 'all'?}
    D -->|Yes| E{intersection_id valid?}
    E -->|No| C
    E -->|Yes| F
    D -->|No: requesting all| F

    F{User has USER role?}
    F -->|No auth| G[/"401 Unauthorized"/]
    F -->|No role| H[/"403 Forbidden"/]
    F -->|Yes| I

    subgraph query ["Build and Execute Query"]
        I{Determine org filter}
        I -->|Scoped org set| J["Filter: org.name = scoped org"]
        I -->|No org + superuser| K["No org filter"]
        I -->|No org + not superuser| L["Filter: org.name IN qualified_orgs"]
        J --> M{Specific intersection?}
        K --> M
        L --> M
        M -->|Yes| N["Add: AND intersection_number = :id"]
        M -->|No| O["No intersection filter"]
        N --> P[("Run SELECT query")]
        O --> P
        P --> Q["Aggregate rows by intersection_number"]
    end

    Q --> R{What was requested?}
    R -->|All| S["intersection_data = list"]
    R -->|Specific + found| T["intersection_data = single object"]
    R -->|Specific + not found| U["intersection_data = empty object"]

    S --> V["Response: just intersection_data"]
    T --> W["Fetch allowed_selections"]
    U --> W
    W --> X["Response: intersection_data + allowed_selections"]

    V --> Y[/"200 OK"/]
    X --> Y
```

---

### PATCH /admin-intersection

Updates an existing intersection's properties and modifies its organization and RSU relationships.

#### Request Body

| Field                     | Type     | Required | Description                                         |
|---------------------------|----------|----------|-----------------------------------------------------|
| `orig_intersection_id`    | integer  | Yes      | Current intersection number (identifies the record) |
| `intersection_id`         | integer  | Yes      | New intersection number (may equal orig)            |
| `ref_pt`                  | object   | Yes      | `{latitude: decimal, longitude: decimal}`           |
| `bbox`                    | object   | No       | `{latitude1, longitude1, latitude2, longitude2: decimal}` |
| `intersection_name`       | string   | No       | Human-readable name                                 |
| `origin_ip`               | IPv4     | No       | Origin IP address                                   |
| `organizations_to_add`    | string[] | Yes      | Organization names to associate                     |
| `organizations_to_remove` | string[] | Yes      | Organization names to disassociate                  |
| `rsus_to_add`             | string[] | Yes      | RSU IPv4 addresses to associate                     |
| `rsus_to_remove`          | string[] | Yes      | RSU IPv4 addresses to disassociate                  |

#### Authorization

1. **Outer check**: OPERATOR role required.
2. **Inner check**: OPERATOR role + INTERSECTION resource type (verifies user has access to this specific intersection).
3. **Org enforcement**: Each organization in `organizations_to_add` and `organizations_to_remove` must be in the user's `qualified_orgs`. Superusers skip this check.

#### Database Operations

The PATCH performs up to five database writes in sequence. Only fields present in the request body are included in the UPDATE statement.

```mermaid
flowchart TD
    A[/"PATCH /admin-intersection"/]
    A --> B{Request body valid?}
    B -->|No| C[/"400 Bad Request"/]
    B -->|Yes| D{Outer: OPERATOR role?}
    D -->|No auth| E[/"401 Unauthorized"/]
    D -->|No role| F[/"403 Forbidden"/]
    D -->|Yes| G{Inner: OPERATOR + intersection access?}
    G -->|No| H[/"403 Forbidden"/]
    G -->|Yes| I["Enforce org restrictions on\norganizations_to_add and\norganizations_to_remove"]
    I --> J{User allowed to modify\nthose organizations?}
    J -->|No| K[/"403 Forbidden"/]
    J -->|Yes| L

    subgraph ops ["Database Operations"]
        L["Step 1: UPDATE intersection record\n(number, ref_pt, optionally bbox/name/ip)\nWHERE intersection_number = orig_intersection_id"]
        L --> M{organizations_to_add\nnon-empty?}
        M -->|Yes| N["Step 2: INSERT intersection_organization rows"]
        M -->|No| O{organizations_to_remove\nnon-empty?}
        N --> O
        O -->|Yes| P["Step 3: DELETE intersection_organization rows"]
        O -->|No| Q{rsus_to_add\nnon-empty?}
        P --> Q
        Q -->|Yes| R["Step 4: INSERT rsu_intersection rows"]
        Q -->|No| S{rsus_to_remove\nnon-empty?}
        R --> S
        S -->|Yes| T["Step 5: DELETE rsu_intersection rows"]
        S -->|No| U
        T --> U
    end

    U["Return success message"] --> V[/"200 OK"/]

    L -.->|IntegrityError| W[/"500: parsed error detail"/]
    L -.->|SQLAlchemyError| X[/"500: generic error"/]
    N -.->|Error| W
    P -.->|Error| W
    R -.->|Error| W
    T -.->|Error| W
```

#### Conditional Field Updates

| Field                 | Included When              |
|-----------------------|----------------------------|
| `intersection_number` | Always                     |
| `ref_pt`              | Always                     |
| `bbox`                | `bbox` is in request body  |
| `intersection_name`   | `intersection_name` is in request body |
| `origin_ip`           | `origin_ip` is in request body |

The WHERE clause uses `orig_intersection_id`, which allows the intersection number to be changed.

#### Relationship Modifications

- **Adding orgs/RSUs**: Inserts rows into `intersection_organization` or `rsu_intersection`. Each row uses subqueries to resolve the surrogate key from the business identifier (org name or RSU IP). Inserts are batched (up to 100 rows per statement).
- **Removing orgs/RSUs**: Deletes rows using `DELETE ... WHERE ... IN (...)` with parameterized values.

#### Success Response

```json
{ "message": "Intersection successfully modified" }
```

#### Error Handling

| Error Type                   | Response                                          |
|------------------------------|---------------------------------------------------|
| `IntegrityError` with detail | 500 with parsed error message from the DB driver  |
| `IntegrityError` no detail   | 500 with `"Encountered unknown issue"`            |
| `SQLAlchemyError`            | 500 with `"Encountered unknown issue executing query"` |

---

### DELETE /admin-intersection

Removes an intersection and all its relationship records.

#### Query Parameters

| Parameter         | Type   | Required | Description                 |
|-------------------|--------|----------|-----------------------------|
| `intersection_id` | string | Yes      | The intersection number to delete |

#### Authorization

1. **Outer check**: OPERATOR role required.
2. **Inner check**: OPERATOR role + INTERSECTION resource type (verifies user has access to this specific intersection).

#### Database Operations

Deletes must happen in this order because foreign keys use `ON DELETE NO ACTION`:

```mermaid
flowchart TD
    A[/"DELETE /admin-intersection?intersection_id=..."/]
    A --> B{intersection_id present and valid?}
    B -->|No| C[/"400 Bad Request"/]
    B -->|Yes| D{Outer: OPERATOR role?}
    D -->|No auth| E[/"401 Unauthorized"/]
    D -->|No role| F[/"403 Forbidden"/]
    D -->|Yes| G{Inner: OPERATOR + intersection access?}
    G -->|No| H[/"403 Forbidden"/]
    G -->|Yes| I

    subgraph ops ["Database Operations (ordered)"]
        I["Step 1: DELETE FROM intersection_organization\nWHERE intersection_id = (subquery by number)"]
        I --> J["Step 2: DELETE FROM rsu_intersection\nWHERE intersection_id = (subquery by number)"]
        J --> K["Step 3: DELETE FROM intersections\nWHERE intersection_number = :id"]
    end

    K --> L["Return success message"]
    L --> M[/"200 OK"/]
```

#### Success Response

```json
{ "message": "Intersection successfully deleted" }
```

---

## Allowed Selections

When a specific intersection is requested via GET, the response includes `allowed_selections`. This provides the lists of organizations and RSUs that the user is allowed to assign to an intersection. The UI uses this to populate dropdowns.

| User Type      | Organizations Returned                         | RSUs Returned                                       |
|----------------|------------------------------------------------|-----------------------------------------------------|
| Superuser      | All orgs from the database (sorted by name)    | All RSU IPs from the database (sorted by IP)        |
| Non-superuser  | Orgs where the user has OPERATOR role or higher | RSUs associated with those orgs (via `rsu_organization` join) |

`allowed_selections` is **not** included when requesting all intersections (`intersection_id = "all"`).

---

## Known Issues to Fix

These are behaviors in the current implementation that should be corrected during migration.

### 1. No Transaction Boundaries

PATCH performs up to 5 independent database writes, each committed separately. DELETE performs 3. A failure partway through leaves the database in a partially modified state. The new implementation should wrap each endpoint's writes in a single database transaction.

### 2. Silent No-Op on Deleting a Nonexistent Intersection

DELETE returns `200` with a success message even when the intersection does not exist (the DELETEs affect zero rows). The new implementation should return `404` if the intersection is not found.

### 3. Fragile IntegrityError Parsing

The PATCH error handler parses the raw error detail string from the pg8000 driver (`e.orig.args[0]["D"]`) and performs string replacements. This is brittle and tied to a specific driver. The new implementation should return structured error information instead.
