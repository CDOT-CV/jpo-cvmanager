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

-- Reserve a model/version regardless of filename, bucket, or provider.
-- Existing duplicate active attempts must be resolved before this migration.
CREATE UNIQUE INDEX uq_firmware_uploads_active_model_version
    ON public.firmware_uploads (model, version)
    WHERE status IN ('PENDING', 'VERIFIED');

COMMENT ON COLUMN public.firmware_images.verified_upload_id IS
    'Upload supplying verification evidence and the exact storage location/version. NULL for legacy images.';
COMMENT ON COLUMN public.manufacturers.firmware_file_extension IS
    'Required source-file suffix and canonical stored-file suffix for RSU firmware uploads.';

COMMIT;
