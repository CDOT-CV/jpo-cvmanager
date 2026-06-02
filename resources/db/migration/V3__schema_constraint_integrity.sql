-- V3__schema_constraint_integrity.sql
--
-- Schema integrity review — constraint enforcement pass.
--
-- Three categories of change:
--
-- 1. UNIQUE constraint on users.keycloak_id
--    Keycloak is the identity provider. The Keycloak custom user provider filters
--    by keycloak_id on every lookup, update, and delete. The column had no uniqueness
--    enforcement and no index, making those operations sequential scans and allowing
--    duplicate keycloak_id values to be inserted.
--
-- 2. UNIQUE constraints on junction tables
--    rsu_organization, user_organization, and intersection_organization had no
--    uniqueness constraints on their natural keys, allowing the same pair to be
--    inserted more than once. rsu_intersection already enforced this correctly and
--    serves as the pattern. Each step removes any existing duplicates before adding
--    the constraint and reports the count via RAISE NOTICE so operators can audit
--    unexpected data loss.
--
-- 3. ON DELETE CASCADE on tightly-coupled child tables
--    rsu_options and consecutive_firmware_upgrade_failures are structural 1:1
--    extensions of the rsus row. Deleting an RSU without cascading left orphaned
--    rows and blocked the delete entirely. user_organization lacked CASCADE on the
--    user_id FK, causing the Keycloak custom user provider's removeUser operation
--    to fail silently for any user with organization memberships (the provider
--    deletes from public.users directly without first removing user_organization rows).
--    user_email_notification already uses CASCADE on user_id for the same reason;
--    this aligns user_organization with that existing pattern.
--
-- Flyway runs each migration in its own transaction (PostgreSQL has transactional DDL),
-- so no explicit BEGIN/COMMIT is used here. DROP CONSTRAINT statements use IF EXISTS so a
-- database whose FK was created under a different constraint name degrades gracefully
-- instead of failing the whole migration.

-- ============================================================
-- 1. UNIQUE on users.keycloak_id
-- ============================================================

ALTER TABLE public.users
    ADD CONSTRAINT users_keycloak_id UNIQUE (keycloak_id);

-- ============================================================
-- 2. Self-healing dedup + UNIQUE on junction tables
-- ============================================================

-- rsu_organization: UNIQUE (rsu_id, organization_id)
-- An RSU can only be assigned to a given organization once. Matches the pattern
-- already established by rsu_intersection UNIQUE (rsu_id, intersection_id).
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

-- user_organization: UNIQUE (user_id, organization_id)
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

-- intersection_organization: UNIQUE (intersection_id, organization_id)
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

-- ============================================================
-- 3. ON DELETE CASCADE on tightly-coupled child tables
-- ============================================================

-- rsu_options: 1:1 extension of rsus. No orphan is meaningful after RSU deletion.
ALTER TABLE public.rsu_options
    DROP CONSTRAINT IF EXISTS fk_rsu_id,
    ADD CONSTRAINT fk_rsu_id FOREIGN KEY (rsu_id)
        REFERENCES public.rsus (rsu_id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE;

-- consecutive_firmware_upgrade_failures: 1:1 extension of rsus.
ALTER TABLE public.consecutive_firmware_upgrade_failures
    DROP CONSTRAINT IF EXISTS fk_rsu_id,
    ADD CONSTRAINT fk_rsu_id FOREIGN KEY (rsu_id)
        REFERENCES public.rsus (rsu_id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE;

-- user_organization: CASCADE on user_id FK.
-- Fixes Keycloak custom user provider removeUser, which deletes from public.users
-- directly. Without CASCADE the delete fails when the user has org memberships.
ALTER TABLE public.user_organization
    DROP CONSTRAINT IF EXISTS fk_user_id,
    ADD CONSTRAINT fk_user_id FOREIGN KEY (user_id)
        REFERENCES public.users (user_id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE;
