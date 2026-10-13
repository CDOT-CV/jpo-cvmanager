BEGIN;

ALTER TABLE public.manufacturers
    ADD COLUMN firmware_file_extension character varying(32);

UPDATE public.manufacturers
SET firmware_file_extension = CASE lower(name)
    WHEN 'commsignia' THEN '.tar.sig'
    WHEN 'yunex' THEN '.tar'
END
WHERE lower(name) IN ('commsignia', 'yunex');

ALTER TABLE public.manufacturers
    ADD CONSTRAINT manufacturers_firmware_file_extension_valid CHECK (
        firmware_file_extension IS NULL
        OR firmware_file_extension ~ '^\.[A-Za-z0-9]+(\.[A-Za-z0-9]+)*$'
    );

ALTER TABLE public.firmware_images
    DROP CONSTRAINT firmware_images_name,
    DROP CONSTRAINT firmware_images_install_package,
    DROP CONSTRAINT firmware_images_version,
    ADD CONSTRAINT firmware_images_model_version_unique UNIQUE (model, version),
    ADD COLUMN verified_upload_id uuid UNIQUE
        REFERENCES public.firmware_uploads (upload_id);

-- Multiple VERIFIED rows are ambiguous because the migration cannot determine
-- which stored object should become the registered firmware image. Stop with a
-- diagnostic rather than silently discarding verification history.
DO $$
DECLARE
    duplicate_verified text;
BEGIN
    SELECT string_agg(
        format('model=%s, version=%L, verified_uploads=%s', model, version, upload_count),
        '; ' ORDER BY model, version)
    INTO duplicate_verified
    FROM (
        SELECT model, version, count(*) AS upload_count
        FROM public.firmware_uploads
        WHERE status = 'VERIFIED'
        GROUP BY model, version
        HAVING count(*) > 1
    ) duplicates;

    IF duplicate_verified IS NOT NULL THEN
        RAISE EXCEPTION USING
            MESSAGE = 'Cannot enforce unique active firmware uploads because duplicate VERIFIED uploads exist',
            DETAIL = duplicate_verified,
            HINT = 'Determine the authoritative upload for each model/version and mark the others FAILED or EXPIRED before retrying the migration.';
    END IF;
END $$;

-- Preserve a VERIFIED upload when present. Otherwise preserve the newest
-- PENDING attempt and expire older attempts for the same model/version.
WITH ranked_active_uploads AS (
    SELECT upload_id,
           status,
           row_number() OVER (
               PARTITION BY model, version
               ORDER BY CASE status WHEN 'VERIFIED' THEN 0 ELSE 1 END,
                        created_at DESC,
                        upload_id DESC
           ) AS precedence
    FROM public.firmware_uploads
    WHERE status IN ('PENDING', 'VERIFIED')
)
UPDATE public.firmware_uploads uploads
SET status = 'EXPIRED',
    finished_at = COALESCE(uploads.finished_at, CURRENT_TIMESTAMP),
    failure_reason = 'SUPERSEDED_MODEL_VERSION'
FROM ranked_active_uploads ranked
WHERE uploads.upload_id = ranked.upload_id
  AND ranked.status = 'PENDING'
  AND ranked.precedence > 1;

-- Reserve a model/version regardless of filename, bucket, or provider.
CREATE UNIQUE INDEX uq_firmware_uploads_active_model_version
    ON public.firmware_uploads (model, version)
    WHERE status IN ('PENDING', 'VERIFIED');

COMMENT ON COLUMN public.firmware_images.verified_upload_id IS
    'Upload supplying verification evidence and the exact storage location/version. NULL for legacy images.';
COMMENT ON COLUMN public.manufacturers.firmware_file_extension IS
    'Required source-file suffix and canonical stored-file suffix for RSU firmware uploads.';

COMMIT;
