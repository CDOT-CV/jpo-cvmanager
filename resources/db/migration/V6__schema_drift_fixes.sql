-- ============================================================
-- High severity schema gaps from cdot_dev_schema_diff.md
-- ============================================================

-- Scope
-- -----
-- This migration restores the high-severity differences identified when
-- comparing the CDOT backup schema to V1__baseline.sql.
--
-- Covered items:
--   1) public.iss_keys missing table
--   2) public.rsu_health missing table
--   3) public.user_email_notification unique constraint + FK delete rules
--   4) public.rsu_intersection unique constraint

BEGIN;

-- ============================================================
-- public.iss_keys
-- ============================================================

CREATE SEQUENCE IF NOT EXISTS public.iss_keys_iss_key_id_seq
   INCREMENT 1
   START 1
   MINVALUE 1
   MAXVALUE 2147483647
   CACHE 1;

CREATE TABLE IF NOT EXISTS public.iss_keys
(
   iss_key_id integer NOT NULL DEFAULT nextval('iss_keys_iss_key_id_seq'::regclass),
   common_name character varying(128) COLLATE pg_catalog.default NOT NULL,
   token character varying(128) COLLATE pg_catalog.default NOT NULL,
   CONSTRAINT iss_keys_pkey PRIMARY KEY (iss_key_id)
);

-- ============================================================
-- public.rsu_health
-- ============================================================

CREATE SEQUENCE IF NOT EXISTS public.rsu_health_rsu_health_id_seq
    INCREMENT 1
    START 1
    MINVALUE 1
    MAXVALUE 2147483647
    CACHE 1;

CREATE TABLE IF NOT EXISTS public.rsu_health
(
    rsu_health_id integer NOT NULL DEFAULT nextval('rsu_health_rsu_health_id_seq'::regclass),
    timestamp timestamp without time zone NOT NULL,
    health integer NOT NULL,
    rsu_id integer NOT NULL,
    CONSTRAINT rsu_health_pkey PRIMARY KEY (rsu_health_id),
    CONSTRAINT fk_rsu_id FOREIGN KEY (rsu_id)
        REFERENCES public.rsus (rsu_id)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);

-- ============================================================
-- public.user_email_notification
-- ============================================================

-- Remove duplicates first so the UNIQUE constraint can be added safely.
DO $$
DECLARE removed_count integer;
BEGIN
    WITH duplicates AS (
        SELECT user_email_notification_id,
               ROW_NUMBER() OVER (
                   PARTITION BY user_id, email_type_id
                   ORDER BY user_email_notification_id
               ) AS rn
        FROM public.user_email_notification
    )
    DELETE FROM public.user_email_notification
    WHERE user_email_notification_id IN (
        SELECT user_email_notification_id FROM duplicates WHERE rn > 1
    );
    GET DIAGNOSTICS removed_count = ROW_COUNT;
    RAISE NOTICE 'user_email_notification: removed % duplicate row(s) before adding UNIQUE constraint', removed_count;
END $$;

ALTER TABLE public.user_email_notification
    DROP CONSTRAINT IF EXISTS user_email_notification_unique,
    ADD CONSTRAINT user_email_notification_unique UNIQUE (user_id, email_type_id);

ALTER TABLE public.user_email_notification
    DROP CONSTRAINT IF EXISTS fk_user_id,
    ADD CONSTRAINT fk_user_id FOREIGN KEY (user_id)
        REFERENCES public.users (user_id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE;

ALTER TABLE public.user_email_notification
    DROP CONSTRAINT IF EXISTS fk_email_type_id,
    ADD CONSTRAINT fk_email_type_id FOREIGN KEY (email_type_id)
        REFERENCES public.email_type (email_type_id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE;

-- ============================================================
-- public.rsu_intersection
-- ============================================================

-- Remove duplicates first so the UNIQUE constraint can be added safely.
DO $$
DECLARE removed_count integer;
BEGIN
    WITH duplicates AS (
        SELECT rsu_intersection_id,
               ROW_NUMBER() OVER (
                   PARTITION BY rsu_id, intersection_id
                   ORDER BY rsu_intersection_id
               ) AS rn
        FROM public.rsu_intersection
    )
    DELETE FROM public.rsu_intersection
    WHERE rsu_intersection_id IN (
        SELECT rsu_intersection_id FROM duplicates WHERE rn > 1
    );
    GET DIAGNOSTICS removed_count = ROW_COUNT;
    RAISE NOTICE 'rsu_intersection: removed % duplicate row(s) before adding UNIQUE constraint', removed_count;
END $$;

ALTER TABLE public.rsu_intersection
    ADD CONSTRAINT rsu_intersection_unique UNIQUE (rsu_id, intersection_id);

COMMIT;
