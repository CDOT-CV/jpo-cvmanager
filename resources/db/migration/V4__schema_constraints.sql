-- ============================================================
-- users
-- ============================================================

-- Keycloak is the identity provider. Every user lookup, update, and delete
-- filters by keycloak_id. Without a UNIQUE constraint the column had no
-- uniqueness enforcement and no index, making those operations sequential
-- scans and allowing duplicate keycloak_id values to be inserted.
ALTER TABLE public.users
    ADD CONSTRAINT users_keycloak_id UNIQUE (keycloak_id);

-- ============================================================
-- firmware_upgrade_rules
-- ============================================================

-- An upgrade path from firmware version A to version B should be defined
-- only once. Duplicate rows cause findFirstByFrom_Id to return ambiguous
-- results and make the upgrade graph inconsistent. Duplicates are removed
-- before the constraint is added; the count is reported so operators can
-- audit unexpected data loss.
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
-- rsu_options
-- ============================================================

-- rsu_options is a 1:1 structural extension of the rsus row — each RSU has
-- exactly one options row. An orphaned rsu_options row after the parent RSU
-- is deleted has no operational meaning and blocks re-insertion of an RSU
-- with the same rsu_id. CASCADE removes the child automatically when the
-- parent RSU is deleted.
ALTER TABLE public.rsu_options
    DROP CONSTRAINT IF EXISTS fk_rsu_id,
    ADD CONSTRAINT fk_rsu_id FOREIGN KEY (rsu_id)
        REFERENCES public.rsus (rsu_id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE;

-- ============================================================
-- consecutive_firmware_upgrade_failures
-- ============================================================

-- 1:1 structural extension of rsus. Same rationale as rsu_options above:
-- the row has no meaning without its parent RSU, and without CASCADE a
-- pending delete is blocked by the child row.
ALTER TABLE public.consecutive_firmware_upgrade_failures
    DROP CONSTRAINT IF EXISTS fk_rsu_id,
    ADD CONSTRAINT fk_rsu_id FOREIGN KEY (rsu_id)
        REFERENCES public.rsus (rsu_id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE;

-- ============================================================
-- snmp_msgfwd_config
-- ============================================================

-- SNMP forwarding config rows are RSU-specific. There is no meaningful
-- forwarding configuration without an owning RSU, and without CASCADE a
-- delete of an RSU that has active forwarding entries is blocked entirely.
ALTER TABLE public.snmp_msgfwd_config
    DROP CONSTRAINT IF EXISTS fk_rsu_id,
    ADD CONSTRAINT fk_rsu_id FOREIGN KEY (rsu_id)
        REFERENCES public.rsus (rsu_id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE;

-- ============================================================
-- max_retry_limit_reached_instances
-- ============================================================

-- Retry exhaustion records are tied to a specific RSU. The composite primary
-- key includes rsu_id, so a row cannot be reassigned to another RSU; CASCADE
-- is the only meaningful behaviour on RSU deletion.
ALTER TABLE public.max_retry_limit_reached_instances
    DROP CONSTRAINT IF EXISTS fk_rsu_id,
    ADD CONSTRAINT fk_rsu_id FOREIGN KEY (rsu_id)
        REFERENCES public.rsus (rsu_id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE;

-- Note: telemetry tables (ping, rsu_health, scms_health) are intentionally
-- left as RESTRICT. These are high-volume time-series tables; RSU deletion
-- should require explicit data pruning before the parent row can be removed,
-- to prevent accidental bulk data loss.

-- ============================================================
-- rsu_organization
-- ============================================================

-- An RSU can only be assigned to a given organization once. Duplicates are
-- removed before the constraint is added; the count is reported so operators
-- can audit unexpected data loss.
DO $$
DECLARE removed_count integer;
BEGIN
    WITH duplicates AS (
        SELECT rsu_organization_id,
               ROW_NUMBER() OVER (
                   PARTITION BY rsu_id, organization_id
                   ORDER BY rsu_organization_id
               ) AS rn
        FROM public.rsu_organization
    )
    DELETE FROM public.rsu_organization
    WHERE rsu_organization_id IN (
        SELECT rsu_organization_id FROM duplicates WHERE rn > 1
    );
    GET DIAGNOSTICS removed_count = ROW_COUNT;
    RAISE NOTICE 'rsu_organization: removed % duplicate row(s) before adding UNIQUE constraint', removed_count;
END $$;

ALTER TABLE public.rsu_organization
    ADD CONSTRAINT rsu_organization_unique UNIQUE (rsu_id, organization_id);

-- A junction row linking an RSU to an organization has no meaning once either
-- parent is deleted. CASCADE on both FK columns ensures the row is cleaned up
-- automatically rather than blocking the parent delete.
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

-- ============================================================
-- rsu_intersection
-- ============================================================

-- UNIQUE (rsu_id, intersection_id) is already enforced in the baseline schema.
-- A junction row linking an RSU to an intersection has no meaning once either
-- parent is deleted. CASCADE on both FKs prevents orphaned rows and allows
-- RSU or intersection deletion without requiring manual cleanup first.
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

-- ============================================================
-- user_organization
-- ============================================================

-- A user can only be assigned to a given organization once (with one role).
DO $$
DECLARE removed_count integer;
BEGIN
    WITH duplicates AS (
        SELECT user_organization_id,
               ROW_NUMBER() OVER (
                   PARTITION BY user_id, organization_id
                   ORDER BY user_organization_id
               ) AS rn
        FROM public.user_organization
    )
    DELETE FROM public.user_organization
    WHERE user_organization_id IN (
        SELECT user_organization_id FROM duplicates WHERE rn > 1
    );
    GET DIAGNOSTICS removed_count = ROW_COUNT;
    RAISE NOTICE 'user_organization: removed % duplicate row(s) before adding UNIQUE constraint', removed_count;
END $$;

ALTER TABLE public.user_organization
    ADD CONSTRAINT user_organization_unique UNIQUE (user_id, organization_id);

-- CASCADE on user_id: the Keycloak custom user provider's removeUser operation
-- deletes directly from public.users without first removing user_organization
-- rows. Without CASCADE the delete fails for any user that has organization
-- memberships. user_email_notification already uses CASCADE on user_id for the
-- same reason; this aligns user_organization with that existing pattern.
ALTER TABLE public.user_organization
    DROP CONSTRAINT IF EXISTS fk_user_id,
    ADD CONSTRAINT fk_user_id FOREIGN KEY (user_id)
        REFERENCES public.users (user_id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE;

-- CASCADE on organization_id: a user-to-org membership row is meaningless once
-- the organization is deleted. Without CASCADE, deleting an organization that
-- still has user memberships is blocked by the child rows in this table.
--
-- role_id is deliberately left RESTRICT: roles are reference data that are
-- never deleted, and the RESTRICT FK actively prevents a role still assigned
-- to any user from being removed.
ALTER TABLE public.user_organization
    DROP CONSTRAINT IF EXISTS fk_organization_id,
    ADD CONSTRAINT fk_organization_id FOREIGN KEY (organization_id)
        REFERENCES public.organizations (organization_id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE;

-- ============================================================
-- intersection_organization
-- ============================================================

-- An intersection can only be assigned to a given organization once.
DO $$
DECLARE removed_count integer;
BEGIN
    WITH duplicates AS (
        SELECT intersection_organization_id,
               ROW_NUMBER() OVER (
                   PARTITION BY intersection_id, organization_id
                   ORDER BY intersection_organization_id
               ) AS rn
        FROM public.intersection_organization
    )
    DELETE FROM public.intersection_organization
    WHERE intersection_organization_id IN (
        SELECT intersection_organization_id FROM duplicates WHERE rn > 1
    );
    GET DIAGNOSTICS removed_count = ROW_COUNT;
    RAISE NOTICE 'intersection_organization: removed % duplicate row(s) before adding UNIQUE constraint', removed_count;
END $$;

ALTER TABLE public.intersection_organization
    ADD CONSTRAINT intersection_organization_unique UNIQUE (intersection_id, organization_id);

-- A junction row linking an intersection to an organization has no meaning
-- once either parent is deleted. CASCADE on both FKs ensures the row is
-- cleaned up automatically rather than blocking the parent delete.
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
