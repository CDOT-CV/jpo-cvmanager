# Admin New Intersection — Migration Specification

## Overview

Migrate the Python `AdminNewIntersection` resource to the existing Java Spring Boot intersection-api. Two operations:

- **GET** — Retrieve organizations and RSUs the user may assign to a new intersection (UI dropdown population).
- **POST** — Create a new intersection with organization and RSU associations.

**Python source**: `services/api/src/admin_new_intersection.py`
**Python tests**: `services/api/tests/src/test_admin_new_intersection.py`

---

## Database Schema

| Table                              | Key Columns                                                                                                                                                                                                 | Purpose                              |
|------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------|
| `public.intersections`             | `intersection_id` (PK, serial), `intersection_number` (unique, varchar 128), `ref_pt` (geography POINT), `bbox` (geography POLYGON, nullable), `intersection_name` (nullable), `origin_ip` (inet, nullable) | Intersection geometry and metadata   |
| `public.intersection_organization` | `intersection_organization_id` (PK), `intersection_id` (FK), `organization_id` (FK)                                                                                                                         | Intersection-to-organization mapping |
| `public.rsu_intersection`          | `rsu_intersection_id` (PK), `rsu_id` (FK), `intersection_id` (FK), UNIQUE(`rsu_id`, `intersection_id`)                                                                                                      | RSU-to-intersection mapping          |

---

## Java Architecture

### Reference Implementation

The existing `AdminIntersection` CRUD endpoints (GET/PATCH/DELETE) are the reference for patterns, conventions, and layer responsibilities. All new code follows the same structure.

### Layer Map

All paths relative to `services/intersection-api/api/src/main/java/us/dot/its/jpo/ode/api/`.

| Layer            | File                                                                                                                                                                         | Action                                                                                                      |
|------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| **Controller**   | `controllers/admin/AdminIntersectionController.java`                                                                                                                         | Add `POST` and GET `allowed-selections` methods to existing controller                                      |
| **Service**      | `services/AdminIntersectionService.java`                                                                                                                                     | Add `createIntersection()`. Expose existing `buildAllowedSelections()` (currently private).                 |
| **Repositories** | `repositories/IntersectionRepository.java`, `IntersectionOrganizationRepository.java`, `RsuIntersectionRepository.java`, `OrganizationRepository.java`, `RsuRepository.java` | Use existing repositories. No new methods expected.                                                         |
| **Request DTO**  | `models/admin/intersection/IntersectionCreate.java`                                                                                                                          | **New file.**                                                                                               |
| **Response DTO** | `models/admin/intersection/AllowedSelections.java`                                                                                                                           | Reuse existing — already matches GET response shape.                                                        |
| **Entity**       | `models/postgres/tables/Intersection.java`                                                                                                                                   | Existing entity — no changes.                                                                               |
| **Mappers**      | `mappers/IntersectionMapper.java`, `mappers/GeometryMapper.java`, `mappers/INetMapper.java`                                                                                  | Reuse existing mappers for entity construction. Add `toEntity(IntersectionCreate)` to `IntersectionMapper`. |
| **Tests**        | `src/test/java/.../services/AdminIntersectionServiceTest.java`, `src/test/java/.../controllers/admin/AdminIntersectionControllerTest.java`                                   | Add test methods to existing test classes.                                                                  |

### Design Principles (from existing code)

- **Controller owns authorization**: `@PreAuthorize` for role/resource checks, method body for org/RSU enforcement. Service assumes valid, authorized inputs.
- **Service owns business logic**: `@Transactional` for multi-step writes. No auth checks in service.
- **DTOs at boundaries**: Jakarta Bean Validation on request DTOs. MapStruct for entity/DTO conversion.
- **Error handling**: `GlobalExceptionHandler` maps exceptions to RFC 7807 ProblemDetail responses.

---

## Request DTO: `IntersectionCreate`

New file at `models/admin/intersection/IntersectionCreate.java`. Follow the `IntersectionPatch` conventions (Lombok `@Getter/@Setter`, `@JsonProperty` for snake_case JSON, `@Schema` for OpenAPI).

| Field              | Java Type                           | JSON Property       | Validation                 | Notes                                              |
|--------------------|-------------------------------------|---------------------|----------------------------|----------------------------------------------------|
| `intersectionId`   | `Integer`                           | `intersection_id`   | `@NotNull`                 | Stored as `intersection_number` (String) in entity |
| `refPt`            | `RefPt`                             | `ref_pt`            | `@NotNull @Valid`          | Reuse existing `RefPt` DTO                         |
| `organizations`    | `List<String>`                      | `organizations`     | `@NotNull @Size(min = 1)`  | Min 1 organization required                        |
| `rsus`             | `List<String>` with IPv4 `@Pattern` | `rsus`              | `@NotNull` (may be empty)  | Reuse IPv4 regex from `IntersectionPatch`          |
| `bbox`             | `Bbox`                              | `bbox`              | `@Valid` (optional)        | Reuse existing `Bbox` DTO                          |
| `intersectionName` | `String`                            | `intersection_name` | (optional)                 |                                                    |
| `originIp`         | `String`                            | `origin_ip`         | `@Pattern` IPv4 (optional) |                                                    |

---

## GET Endpoint: Allowed Selections

### Route

`GET /admin/intersections/allowed-selections`

### Authorization

```java
@PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('USER')")
```

### Implementation

Expose the existing `buildAllowedSelections()` method (currently `private` in `AdminIntersectionService`). Change visibility to package-private or public, then call it from the new controller method.
Return the existing `AllowedSelections` DTO.

### Response

```json
{
  "organizations": [
    "Org A",
    "Org B"
  ],
  "rsus": [
    "192.168.1.1",
    "192.168.1.2"
  ]
}
```

### Behavior by User Type

| User Type      | Organizations Returned                                                     | RSUs Returned                                  |
|----------------|----------------------------------------------------------------------------|------------------------------------------------|
| Super user     | The organization data from DB using the provided Organization header value | All RSUs from DB accesible by the organization |
| Non-super user | Orgs where user holds OPERATOR+ role                                       | RSUs associated with those orgs                |

**Note**: GET requires `USER` role for access, but `buildAllowedSelections()` filters orgs by `OPERATOR` threshold. A user with only `USER` role in all orgs passes the gate but gets empty results.
This matches the Python behavior and is intentional.

---

## POST Endpoint: Create Intersection

### Route

`POST /admin/intersections`

### Authorization

```java
@PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('OPERATOR')")
```

### Example Request Body

```json
{
  "intersection_id": 12109,
  "ref_pt": {
    "latitude": 40.123,
    "longitude": -105.456
  },
  "organizations": [
    "Org A"
  ],
  "rsus": [
    "192.168.1.2"
  ],
  "bbox": {
    "latitude1": 40.111,
    "longitude1": -105.444,
    "latitude2": 40.133,
    "longitude2": -105.466
  },
  "intersection_name": "Main St & 1st Ave",
  "origin_ip": "10.0.0.1"
}
```

### Controller Method: Organization and RSU Enforcement

Follow the `patchIntersection()` pattern. This enforcement is done **in the controller**, not the service.

```java
if (!permissionService.isSuperUser()) {
CvManagerAuthToken token = permissionService.getCvManagerAuthToken();
List<String> qualifiedOrgs = token != null
  ? token.getQualifiedOrgList("OPERATOR")
  : Collections.emptyList();
Set<String> qualifiedOrgSet = new HashSet<>(qualifiedOrgs);

if (!qualifiedOrgSet.containsAll(create.getOrganizations())) {
  throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "Not authorized to modify one or more of the specified organizations");
}

if (!create.getRsus().isEmpty() &&!permissionService.hasRsus(create.getRsus(), "OPERATOR")) {
  throw newResponseStatusException(HttpStatus.FORBIDDEN,
          "Not authorized to modify one or more of the specified RSUs");
}
}
```

### Service Method: `createIntersection(IntersectionCreate create)`

Must be annotated `@Transactional`. The Python implementation commits each INSERT independently — a failure on step 2 or 3 leaves orphaned data. The Java implementation wraps all writes in a single
transaction.

**Write sequence:**

1. **Build and save `Intersection` entity**
    - `intersectionNumber` = `create.getIntersectionId().toString()`
    - `refPt` = `GeometryMapper.toPoint(create.getRefPt())`
    - `bbox` = `GeometryMapper.toPolygon(create.getBbox())` (null if absent)
    - `intersectionName` = `create.getIntersectionName()` (null if absent)
    - `originIp` = `INetMapper.mapStringToInetAddress(create.getOriginIp())` (null if absent)
    - Save via `intersectionRepository.save(intersection)` — flush to get the managed entity with PK

2. **Create organization associations**
    - Look up orgs via `organizationRepository.findByNameIn(create.getOrganizations())`
    - If count doesn't match request → throw error identifying which org(s) were not found
    - Build `IntersectionOrganization` entities linking saved intersection to each org
    - Save via `intersectionOrganizationRepository.saveAll(...)`

3. **Create RSU associations** (skip if `rsus` is empty)
    - Convert IP strings via `INetMapper.mapStringToInetAddress()`
    - Look up RSUs via `rsuRepository.findByIpv4AddressIn(inetAddresses)`
    - If count doesn't match request → throw error identifying which RSU(s) were not found
    - Build `RsuIntersection` entities linking saved intersection to each RSU
    - Save via `rsuIntersectionRepository.saveAll(...)`

**Improvement over Python**: Validate that all referenced organizations and RSUs exist upfront before writing, rather than relying on subquery nulls and IntegrityErrors. Throw an 
EntityNotFoundException with a clear message identifying the missing resource.

### Success Response

Return `void` (HTTP 200) to match existing PATCH/DELETE conventions in the controller.

### Error Responses

Handled by existing `GlobalExceptionHandler` (RFC 7807 ProblemDetail format):

| Condition                       | HTTP Status | Java Exception                              |
|---------------------------------|-------------|---------------------------------------------|
| Not authenticated               | 401         | Spring Security                             |
| Insufficient role               | 403         | `AccessDeniedException` via `@PreAuthorize` |
| Validation failure              | 400         | `MethodArgumentNotValidException`           |
| Unauthorized org or RSU         | 403         | `ResponseStatusException(FORBIDDEN)`        |
| Org or RSU not found in DB      | 404         | `EntityNotFoundException`                   |
| Duplicate `intersection_number` | 500         | `DataIntegrityViolationException`           |
| General DB error                | 500         | `DataAccessException`                       |

---

## Migration Decisions

| Decision                                                   | Rationale                                                                                                                                               |
|------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| Add to existing `AdminIntersectionController`              | RESTful: POST to the same resource collection as GET/PATCH/DELETE                                                                                       |
| Reuse `buildAllowedSelections()` for GET                   | Already implements correct super-user vs. regular-user filtering                                                                                        |
| **Do NOT migrate `check_safe_input`**                      | Python uses string-interpolated SQL and relies on character filtering as a SQL injection guard. JPA parameterized queries eliminate this need entirely. |
| Wrap writes in `@Transactional`                            | Python's independent commits leave orphaned data on partial failure                                                                                     |
| Validate org/RSU existence before writing                  | Python relies on subquery NULLs + IntegrityError; explicit checks give clear 404 messages                                                               |
| Fix org restriction enforcement                            | Python version is broken — silently swallows all exceptions (see bug note above)                                                                        |
| `intersectionId` is Integer in DTO, String in entity       | Matches existing `IntersectionPatch` convention; convert via `.toString()` in service                                                                   |
| Add `toEntity(IntersectionCreate)` to `IntersectionMapper` | Follow MapStruct pattern established by `partialUpdate(Intersection, IntersectionPatch)`                                                                |

---

## Required Test Coverage

### Service: `createIntersection()`

| Test Case                                          | Expected Outcome                                   |
|----------------------------------------------------|----------------------------------------------------|
| All fields populated (happy path)                  | Intersection + org + RSU associations saved        |
| Optional fields omitted (no bbox, name, origin_ip) | Intersection saved with nulls for optional columns |
| Empty RSU list                                     | RSU association step skipped, no error             |
| Duplicate `intersectionNumber`                     | `DataIntegrityViolationException` propagates       |
| Non-existent organization name                     | `EntityNotFoundException` with clear message       |
| Non-existent RSU IP                                | `EntityNotFoundException` with clear message       |

### Service: `buildAllowedSelections()` (if not already tested)

| Test Case                                     | Expected Outcome                           |
|-----------------------------------------------|--------------------------------------------|
| Super user                                    | Returns all organizations and all RSU IPs  |
| Non-super user with OPERATOR orgs             | Returns only qualified orgs and their RSUs |
| Non-super user with only USER role everywhere | Returns empty org and RSU lists            |

### Controller: Authorization

| Test Case                                | Expected Outcome |
|------------------------------------------|------------------|
| Super user: bypasses org/RSU enforcement | 200              |
| Non-super user with valid orgs and RSUs  | 200              |
| Non-super user with unauthorized org     | 403              |
| Non-super user with unauthorized RSU     | 403              |
| Non-super user without OPERATOR role     | 403              |

### Controller: Validation

| Test Case                            | Expected Outcome                |
|--------------------------------------|---------------------------------|
| Missing `intersection_id`            | 400                             |
| Missing `ref_pt`                     | 400                             |
| Missing `ref_pt.latitude`            | 400                             |
| Empty `organizations` list           | 400 (violates `@Size(min = 1)`) |
| Invalid IPv4 in `rsus`               | 400                             |
| Valid request with empty `rsus` list | 200 (allowed)                   |
