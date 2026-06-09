-- V4__add_unique_org_constraints.sql
-- Adds UNIQUE constraints to the three many-to-many organization join tables to
-- prevent duplicate membership rows that could cause silent data integrity issues.
--
-- Constraints added:
--   user_organization_unique         (user_id,         organization_id)
--   rsu_organization_unique          (rsu_id,          organization_id)
--   intersection_organization_unique (intersection_id, organization_id)
--
-- Each statement is wrapped in a DO block that catches duplicate_table (the SQLSTATE
-- Postgres raises when a constraint with that name already exists), silently skipping
-- if the constraint was applied manually outside of Flyway.

BEGIN;

DO $$ BEGIN
    ALTER TABLE public.user_organization ADD CONSTRAINT user_organization_unique UNIQUE (user_id, organization_id);
EXCEPTION WHEN duplicate_table THEN NULL; END $$;

DO $$ BEGIN
    ALTER TABLE public.rsu_organization ADD CONSTRAINT rsu_organization_unique UNIQUE (rsu_id, organization_id);
EXCEPTION WHEN duplicate_table THEN NULL; END $$;

DO $$ BEGIN
    ALTER TABLE public.intersection_organization ADD CONSTRAINT intersection_organization_unique UNIQUE (intersection_id, organization_id);
EXCEPTION WHEN duplicate_table THEN NULL; END $$;

COMMIT;