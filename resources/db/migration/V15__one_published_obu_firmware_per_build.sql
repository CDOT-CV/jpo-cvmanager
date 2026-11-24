BEGIN;

-- An OBU installs the newest published firmware that matches its own build
-- (device type, variant, file system, and secure boot), so only one release per
-- build is published. Keep the most recently published release of each build.
UPDATE public.obu_firmware_images AS image
SET published = false,
    published_at = NULL,
    published_by = NULL
FROM (
    SELECT obu_firmware_id,
           row_number() OVER (
               PARTITION BY manufacturer, device_type, variant, writable_system_partition, secure_boot
               ORDER BY published_at DESC, obu_firmware_id DESC) AS release_rank
    FROM public.obu_firmware_images
    WHERE published
) AS ranked
WHERE image.obu_firmware_id = ranked.obu_firmware_id
  AND ranked.release_rank > 1;

CREATE UNIQUE INDEX uq_obu_firmware_images_published_build
    ON public.obu_firmware_images (manufacturer, device_type, variant, writable_system_partition, secure_boot)
    NULLS NOT DISTINCT
    WHERE published;

COMMENT ON INDEX public.uq_obu_firmware_images_published_build IS
    'Allows one published OBU firmware release per manufacturer build.';

COMMIT;
