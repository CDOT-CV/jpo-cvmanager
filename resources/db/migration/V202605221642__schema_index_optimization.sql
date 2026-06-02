-- V202605221642__schema_index_optimization.sql
--
-- Schema integrity review — index optimization pass.
--
-- Two categories of change:
--
-- 1. Remove redundant and unused indexes
--    PostgreSQL automatically creates a B-tree index to enforce each UNIQUE
--    constraint and PRIMARY KEY. Explicit indexes on those same columns are
--    redundant: they duplicate the implicit index, consume extra storage, and
--    add write overhead on every INSERT/UPDATE/DELETE for zero read benefit.
--    The planner will use the constraint-backed index with identical query plans.
--    Nine indexes are dropped:
--      - Six single-column explicit indexes duplicating a PK or UNIQUE constraint.
--      - One composite index (ipv4_address, rsu_id) where the leading column is
--        already unique, making the second column unreachable as an additional
--        filter; all known query patterns are already served by the UNIQUE index.
--      - One explicit index (user_id, organization_id) on user_organization made
--        redundant by the UNIQUE constraint added in the constraint integrity
--        migration (same columns, same order).
--      - One single-column timestamp index on scms_health with no active query
--        path (confirmed by full audit of ScmsHealthRepository: no operation
--        filters scms_health by timestamp alone).
--
-- 2. Add composite (rsu_id, timestamp DESC) indexes on telemetry tables
--    ping, rsu_health, and scms_health each have a NOT NULL FK to rsus.rsu_id.
--    PostgreSQL does not create indexes on FK columns automatically. Without an
--    index on rsu_id, every per-RSU query on these tables is a full sequential
--    scan. The README documents that retaining more than 24 hours of data per RSU
--    in ping and rsu_health causes noticeable CV Manager map load slowdowns.
--    The composite (rsu_id, timestamp DESC) order covers:
--      - Per-RSU lookups (WHERE rsu_id = ?)
--      - Timestamp-ordered result sets (ORDER BY timestamp DESC)
--      - Pruning queries that filter by rsu_id before deleting old rows
--    For scms_health the composite additionally allows PostgreSQL to satisfy
--    ROW_NUMBER() OVER (PARTITION BY rsu_id ORDER BY timestamp DESC) by walking
--    the index in partition order rather than sorting in memory.
--
-- Flyway runs each migration in its own transaction, so no explicit BEGIN/COMMIT is used.

-- ============================================================
-- 1. Drop redundant indexes
-- ============================================================

-- Redundant with users_pkey (PRIMARY KEY constraint)
DROP INDEX IF EXISTS public.idx_users_user_id;

-- Redundant with users_email UNIQUE constraint
DROP INDEX IF EXISTS public.idx_users_email;

-- Redundant with intersection_pkey (PRIMARY KEY constraint)
DROP INDEX IF EXISTS public.idx_intersection_id;

-- Redundant with intersection_intersection_number UNIQUE constraint
DROP INDEX IF EXISTS public.idx_intersections_intersection_number;

-- Redundant with rsu_ipv4_address UNIQUE constraint
DROP INDEX IF EXISTS public.idx_rsus_ipv4_address;

-- Redundant with organizations_name UNIQUE constraint
DROP INDEX IF EXISTS public.idx_organizations_name;

-- Redundant: ipv4_address uniquely identifies one row, so rsu_id is always
-- determined by the first column lookup. The UNIQUE constraint index on
-- ipv4_address alone serves all known query patterns against rsus.
DROP INDEX IF EXISTS public.idx_rsus_ipv4_rsu_id;

-- Redundant with the user_organization_unique UNIQUE constraint added in
-- V202605221641. Both cover (user_id, organization_id) in the same order.
DROP INDEX IF EXISTS public.idx_user_organization;

-- Unused: ScmsHealthRepository audit confirms no query filters scms_health
-- by timestamp without also filtering by rsu_id. Replaced below by the
-- composite (rsu_id, timestamp DESC) index.
DROP INDEX IF EXISTS public.idx_scms_health_timestamp;

-- ============================================================
-- 2. New composite indexes on telemetry tables
-- ============================================================

CREATE INDEX idx_ping_rsu_id_timestamp
    ON public.ping (rsu_id, timestamp DESC);

COMMENT ON INDEX public.idx_ping_rsu_id_timestamp IS
    'Covers per-RSU ping lookups (WHERE rsu_id = ?) and timestamp-ordered results. '
    'rsu_id is a FK column — PostgreSQL does not index FK columns automatically. '
    'Without this index every per-RSU map load query is a full sequential scan. '
    'Also accelerates pruning queries that filter by rsu_id before deleting old rows.';

CREATE INDEX idx_rsu_health_rsu_id_timestamp
    ON public.rsu_health (rsu_id, timestamp DESC);

COMMENT ON INDEX public.idx_rsu_health_rsu_id_timestamp IS
    'Same rationale as idx_ping_rsu_id_timestamp. '
    'rsu_health mirrors the ping table in growth pattern and query shape.';

CREATE INDEX idx_scms_health_rsu_id_timestamp
    ON public.scms_health (rsu_id, timestamp DESC);

COMMENT ON INDEX public.idx_scms_health_rsu_id_timestamp IS
    'Replaces the dropped idx_scms_health_timestamp (single-column, unused). '
    'The leading rsu_id column supports per-RSU certificate health lookups. '
    'The trailing timestamp DESC column allows PostgreSQL to satisfy '
    'ROW_NUMBER() OVER (PARTITION BY rsu_id ORDER BY timestamp DESC) '
    'by walking the index in partition order rather than sorting in memory.';
