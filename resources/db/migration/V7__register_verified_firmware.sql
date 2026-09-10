BEGIN;

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

COMMIT;
