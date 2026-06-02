-- V202605261241__remaining_relational_constraints.sql
--
-- Schema integrity review -- remaining constraint enforcement pass.
--
-- Three categories of change:
--
-- 1. UNIQUE constraint on firmware_upgrade_rules(from_id, to_id)
--    An upgrade path from firmware version A to version B should only be defined
--    once. Without uniqueness enforcement duplicate rows can be inserted, causing
--    firmware upgrade rule lookups (findFirstByFrom_Id) to return ambiguous results
--    and making the upgrade graph inconsistent. A dedup step runs first and reports
--    any rows removed via RAISE NOTICE so operators can audit unexpected data loss.
--
-- 2. ON DELETE CASCADE on junction table FKs
--    rsu_intersection, rsu_organization, and intersection_organization each hold
--    rows that link two parent entities. A junction row is meaningless when either
--    parent is deleted. Without CASCADE, deleting an RSU, intersection, or
--    organization is blocked by child rows in these tables, forcing callers to
--    manually clean up children before the parent delete can succeed. CASCADE
--    aligns these tables with the semantics already established by user_organization
--    (cascade on user_id FK, added in V202605221641) and user_email_notification
--    (cascade on both FKs in the baseline).
--
--    WARNING: adding CASCADE to junction tables means that deleting a parent row
--    will silently remove all associated junction rows. Application code that
--    deletes RSUs, intersections, or organizations must account for this behaviour.
--    All three junction tables now cascade on BOTH FK columns, so deletion of
--    either parent removes the row.
--
-- 3. ON DELETE CASCADE on structural child tables
--    snmp_msgfwd_config rows are RSU-specific SNMP forwarding configuration. An
--    orphaned config row with no owning RSU has no operational meaning and prevents
--    RSU deletion.
--
--    max_retry_limit_reached_instances tracks firmware upgrade retry exhaustion per
--    RSU. Once the RSU is deleted, the historical record serves no purpose and its
--    composite PK (rsu_id, reached_at) would block RSU deletion without CASCADE.
--
-- Telemetry tables (ping, rsu_health, scms_health) are intentionally left as
-- RESTRICT. These are high-volume time-series tables; RSU deletion should require
-- explicit data pruning before the parent row can be removed, to prevent accidental
-- bulk data loss. See resources/db/README.md for the recommended pruning approach.
--
-- Flyway runs each migration in its own transaction (PostgreSQL has transactional DDL),
-- so no explicit BEGIN/COMMIT is used here. DROP CONSTRAINT statements use IF EXISTS so a
-- database whose FK was created under a different constraint name degrades gracefully
-- instead of failing the whole migration.

-- ============================================================
-- 1. UNIQUE on firmware_upgrade_rules(from_id, to_id)
-- ============================================================

DO $$
DECLARE removed_count integer;
BEGIN
    WITH duplicates AS (
        SELECT firmware_upgrade_rule_id,
               ROW_NUMBER() OVER (
                   PARTITION BY from_id, to_id
                   ORDER BY firmware_upgrade_rule_id
               ) AS rn
        FROM public.firmware_upgrade_rules
    )
    DELETE FROM public.firmware_upgrade_rules
    WHERE firmware_upgrade_rule_id IN (
        SELECT firmware_upgrade_rule_id FROM duplicates WHERE rn > 1
    );
    GET DIAGNOSTICS removed_count = ROW_COUNT;
    RAISE NOTICE 'firmware_upgrade_rules: removed % duplicate row(s) before adding UNIQUE constraint', removed_count;
END $$;

ALTER TABLE public.firmware_upgrade_rules
    ADD CONSTRAINT firmware_upgrade_rules_from_to_unique UNIQUE (from_id, to_id);

-- ============================================================
-- 2. ON DELETE CASCADE on junction table FKs
-- ============================================================

-- rsu_intersection: CASCADE on rsu_id and intersection_id.
-- A link between an RSU and an intersection has no meaning without either parent.
ALTER TABLE public.rsu_intersection
    DROP CONSTRAINT IF EXISTS fk_rsu_id,
    ADD CONSTRAINT fk_rsu_id FOREIGN KEY (rsu_id)
        REFERENCES public.rsus (rsu_id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE;

ALTER TABLE public.rsu_intersection
    DROP CONSTRAINT IF EXISTS fk_intersection_id,
    ADD CONSTRAINT fk_intersection_id FOREIGN KEY (intersection_id)
        REFERENCES public.intersections (intersection_id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE;

-- rsu_organization: CASCADE on rsu_id and organization_id.
-- An RSU-to-org membership row is meaningless without either parent.
ALTER TABLE public.rsu_organization
    DROP CONSTRAINT IF EXISTS fk_rsu_id,
    ADD CONSTRAINT fk_rsu_id FOREIGN KEY (rsu_id)
        REFERENCES public.rsus (rsu_id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE;

ALTER TABLE public.rsu_organization
    DROP CONSTRAINT IF EXISTS fk_organization_id,
    ADD CONSTRAINT fk_organization_id FOREIGN KEY (organization_id)
        REFERENCES public.organizations (organization_id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE;

-- intersection_organization: CASCADE on intersection_id and organization_id.
-- An intersection-to-org membership row is meaningless without either parent.
ALTER TABLE public.intersection_organization
    DROP CONSTRAINT IF EXISTS fk_intersection_id,
    ADD CONSTRAINT fk_intersection_id FOREIGN KEY (intersection_id)
        REFERENCES public.intersections (intersection_id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE;

ALTER TABLE public.intersection_organization
    DROP CONSTRAINT IF EXISTS fk_organization_id,
    ADD CONSTRAINT fk_organization_id FOREIGN KEY (organization_id)
        REFERENCES public.organizations (organization_id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE;

-- ============================================================
-- 3. ON DELETE CASCADE on structural child tables
-- ============================================================

-- snmp_msgfwd_config: CASCADE on rsu_id.
-- SNMP forwarding config rows are RSU-specific. Without CASCADE, deleting an RSU
-- that has active forwarding entries is blocked.
ALTER TABLE public.snmp_msgfwd_config
    DROP CONSTRAINT IF EXISTS fk_rsu_id,
    ADD CONSTRAINT fk_rsu_id FOREIGN KEY (rsu_id)
        REFERENCES public.rsus (rsu_id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE;

-- max_retry_limit_reached_instances: CASCADE on rsu_id.
-- Retry exhaustion records are tied to a specific RSU. The composite PK includes
-- rsu_id, so a row cannot be reassigned to another RSU; CASCADE is the only
-- meaningful behaviour on RSU deletion.
ALTER TABLE public.max_retry_limit_reached_instances
    DROP CONSTRAINT IF EXISTS fk_rsu_id,
    ADD CONSTRAINT fk_rsu_id FOREIGN KEY (rsu_id)
        REFERENCES public.rsus (rsu_id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE;

-- ============================================================
-- 4. user_organization: CASCADE on organization_id
-- ============================================================

-- Parity with rsu_organization and intersection_organization above: a user-to-org
-- membership row is meaningless once the organization is deleted, so deleting an
-- organization that still has user memberships should cascade those rows away (rather
-- than being blocked by RESTRICT). user_id already cascades (V202605221641).
--
-- role_id is deliberately left RESTRICT: roles are reference data that are never deleted,
-- and the RESTRICT FK actively enforces that a role still assigned to any user cannot be
-- removed.
ALTER TABLE public.user_organization
    DROP CONSTRAINT IF EXISTS fk_organization_id,
    ADD CONSTRAINT fk_organization_id FOREIGN KEY (organization_id)
        REFERENCES public.organizations (organization_id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE;
