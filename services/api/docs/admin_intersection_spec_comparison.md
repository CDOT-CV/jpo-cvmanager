# AdminIntersection Spec Comparison

Comparison of `admin_intersection_spec.md` (hereafter **Spec**) and `admin_intersection_migration_spec.md` (hereafter **Migration**) against each other and against the source code in `admin_intersection.py`, `admin_new_intersection.py`, and `auth_tools.py`.

Findings are grouped by severity.

---

## Errors in Both Documents

### 1. Schema validation is shown before permission checking, but the code does the opposite

Both documents show the same incorrect order for all three endpoints (GET, PATCH, DELETE):

| Step | Both Documents Show | Actual Code Order |
|------|--------------------|--------------------|
| 1    | Schema validation  | `@require_permission` decorator (auth + role check) |
| 2    | Permission check   | Schema validation (inside the function body) |

The `@require_permission` decorator wraps the endpoint function. It runs first, checking authentication and role. Only if those pass does the function body execute, where schema validation happens.

**Impact on migration**: If both auth and request body are invalid, the current code returns **401/403** (auth fails first). Both documents imply it would return **400** (schema fails first). The new implementation should check auth before validating input, matching the current behavior.

**Source**: `admin_intersection.py:373-376` (decorator on `get`), `admin_intersection.py:379-391` (schema validation inside `get` body). Same pattern on `patch` (lines 403-412) and `delete` (lines 423-431).

### 2. Neither document notes that the two GET validation schemas are identical

The GET endpoint validates `request.args` against `AdminIntersectionGetAllSchema`, then (if `intersection_id != "all"`) validates again against `AdminIntersectionGetDeleteSchema`. Both schemas define the same single field:

```python
class AdminIntersectionGetAllSchema(Schema):
    intersection_id = fields.Str(required=True)

class AdminIntersectionGetDeleteSchema(Schema):
    intersection_id = fields.Str(required=True)
```

The second validation is a no-op. Both documents present this as meaningful "additional validation" when it does nothing. The Spec diagram labels it "Additional schema validation on intersection_id" and the Migration diagram labels it "intersection_id valid?".

**Impact on migration**: The new implementation does not need to replicate this redundant check. A single validation that `intersection_id` is a required string is sufficient.

---

## Gaps in Migration (present in Spec, missing from Migration)

### 3. DELETE error handling omitted from Migration

The Spec shows that a database error during any DELETE step produces a **500 Internal Server Error**. The Migration's DELETE diagram has no error paths at all.

Note: unlike PATCH (which has explicit `try/except` with specific error messages), DELETE has **no explicit error handling** in the code. Database exceptions propagate up to Flask's default error handler, which returns a generic 500. This nuance is not called out clearly in either document.

**Impact on migration**: The new implementation should handle database errors during DELETE explicitly rather than relying on framework defaults. Both documents agree this should be a 500 response.

### 4. `check_safe_input` omitted from Migration

The Spec documents the `check_safe_input` validation step (blocked characters, exempt fields, recursive behavior) and includes it in the PATCH flow diagram. The Migration omits it entirely.

This was an intentional omission per user instruction ("You do not need to include input validation rules"). Both documents agree (Spec via migration note, Migration via omission) that the new implementation should use parameterized queries and can omit this check. **No action needed.**

---

## Gaps in Spec (present in Migration, missing from Spec)

### 5. Spec ER diagram omits the `organizations` and `rsus` lookup tables

The Spec's ER diagram shows only the three core tables (`intersections`, `intersection_organization`, `rsu_intersection`). It lists `organizations` and `rsus` as text below the diagram.

The Migration's ER diagram includes all five tables with their relationships to the junction tables, making it clearer how JOINs resolve business identifiers (org name, RSU IP) to surrogate keys.

**Recommendation**: Use the Migration's more complete ER diagram.

### 6. Neither document shows the `rsu_organization` table

`get_allowed_selections` for non-superusers fetches RSUs by joining through `rsu_organization` (not `rsu_intersection`). Both documents mention this table in text ("via `rsu_organization` join") but neither includes it in the ER diagram.

**Impact on migration**: The `rsu_organization` table is a dependency for the allowed selections feature. It should be acknowledged in the schema section so the implementer knows the full set of tables involved.

---

## Minor Differences (compatible, but worth noting)

### 7. Superuser bypass described at different levels

The Spec states a general principle in the Role Hierarchy section: "Superusers bypass org-scoped restrictions." The Migration describes superuser behavior only in context-specific places (organization filtering, org enforcement, allowed selections).

Both are accurate. The Spec's general statement is useful as a guiding principle. The Migration's approach avoids ambiguity about exactly which checks are bypassed.

**Recommendation**: Include both — a general principle and context-specific descriptions.

### 8. IntegrityError parsing detail level differs

The Spec describes the exact string replacements performed on the error detail: `(` to `"`, `)` to `"`, `=` to ` = `. The Migration summarizes this as "parsed error message from the DB driver."

Both documents list this as a known issue to replace with structured error information. The extra detail in the Spec is only relevant if replicating the current behavior exactly, which both documents recommend against.

### 9. Spec's conditional field update table includes SQL fragments

The Spec's table for PATCH conditional updates includes the actual SQL fragments (e.g., `SET intersection_number=:intersection_id`, `ref_pt=ST_GeomFromText(...)`). The Migration's table lists only field names and conditions.

Both convey the same information. The SQL fragments in the Spec are helpful implementation reference but are framework-specific (raw SQL) and may not apply directly in the new implementation.

---

## Summary

| # | Finding | Severity | In Spec | In Migration | Action |
|---|---------|----------|---------|--------------|--------|
| 1 | Auth runs before schema validation; both diagrams show the opposite | Error | Yes | Yes | Fix flow diagrams in both documents |
| 2 | Two GET schemas are identical; "additional validation" is a no-op | Error | Yes | Yes | Note as redundant; do not replicate |
| 3 | DELETE error handling paths missing from Migration diagram | Gap | Correct | Missing | Add error paths to Migration DELETE diagram |
| 4 | `check_safe_input` omitted from Migration | Gap | Present | Omitted | Intentional per user instruction; no action |
| 5 | `organizations` and `rsus` tables missing from Spec ER diagram | Gap | Missing | Present | Use Migration's ER diagram |
| 6 | `rsu_organization` table missing from both ER diagrams | Gap | Missing | Missing | Add to ER diagram |
| 7 | Superuser bypass described at different scope levels | Minor | General | Contextual | Combine both approaches |
| 8 | IntegrityError parsing at different detail levels | Minor | Detailed | Summary | No action (both recommend replacing it) |
| 9 | SQL fragments in Spec's conditional update table | Minor | Present | Absent | No action (implementation detail) |
