BEGIN;

-- OBU firmware upload intents, from signed URL creation through checksum
-- verification. The lifecycle mirrors firmware_uploads (V6), but OBU firmware is
-- identified by its manufacturer and original vendor file name instead of an RSU
-- model and version.
CREATE TABLE public.obu_firmware_uploads
(
    upload_id uuid NOT NULL,
    manufacturer integer NOT NULL,
    file_name character varying(128) NOT NULL,
    device_type character varying(16) NOT NULL,
    release_version character varying(128) NOT NULL,
    variant character varying(128),
    writable_system_partition boolean,
    secure_boot boolean,
    content_type character varying(255) NOT NULL,
    storage_provider character varying(32) NOT NULL,
    storage_container character varying(255) NOT NULL,
    object_name text NOT NULL,
    expected_size bigint NOT NULL,
    checksum_algorithm character varying(32) NOT NULL,
    expected_checksum character varying(128) NOT NULL,
    status character varying(16) NOT NULL,
    created_by character varying(255) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    verified_at timestamp with time zone,
    finished_at timestamp with time zone,
    failure_reason character varying(255),
    provider_object_version text,
    observed_checksum character varying(128),
    CONSTRAINT obu_firmware_uploads_pkey PRIMARY KEY (upload_id),
    CONSTRAINT obu_firmware_uploads_manufacturer_fkey FOREIGN KEY (manufacturer)
        REFERENCES public.manufacturers (manufacturer_id)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT obu_firmware_uploads_expected_size_positive CHECK (expected_size > 0),
    CONSTRAINT obu_firmware_uploads_status_valid CHECK (status IN ('PENDING', 'VERIFIED', 'FAILED', 'EXPIRED')),
    CONSTRAINT obu_firmware_uploads_device_type_valid CHECK (device_type IN ('OBU', 'RSU'))
);

CREATE INDEX idx_obu_firmware_uploads_manufacturer
    ON public.obu_firmware_uploads (manufacturer);

CREATE INDEX idx_obu_firmware_uploads_status_expires_at
    ON public.obu_firmware_uploads (status, expires_at);

-- One live upload intent per storage destination, while permitting retries after
-- failure or expiry.
CREATE UNIQUE INDEX uq_obu_firmware_uploads_active_destination
    ON public.obu_firmware_uploads (storage_provider, storage_container, object_name)
    WHERE status IN ('PENDING', 'VERIFIED');

-- OBUs download firmware by file name, so a name can only be in use once per
-- manufacturer.
CREATE UNIQUE INDEX uq_obu_firmware_uploads_active_file_name
    ON public.obu_firmware_uploads (manufacturer, file_name)
    WHERE status IN ('PENDING', 'VERIFIED');

CREATE INDEX idx_obu_firmware_uploads_finished_retention
    ON public.obu_firmware_uploads (status, finished_at)
    WHERE status IN ('FAILED', 'EXPIRED');

-- The catalog of OBU firmware the OBU OTA server can serve. Rows are created when
-- an upload is verified, or registered directly for files on the OTA server's
-- local firmware mount (storage_provider 'local').
CREATE TABLE public.obu_firmware_images
(
    obu_firmware_id serial NOT NULL,
    manufacturer integer NOT NULL,
    file_name character varying(128) NOT NULL,
    device_type character varying(16) NOT NULL,
    release_version character varying(128) NOT NULL,
    variant character varying(128),
    writable_system_partition boolean,
    secure_boot boolean,
    storage_provider character varying(32) NOT NULL,
    storage_container character varying(255) NOT NULL,
    object_name text NOT NULL,
    content_length bigint,
    checksum_algorithm character varying(32),
    checksum character varying(128),
    provider_object_version text,
    verified_upload_id uuid,
    registered_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published boolean NOT NULL DEFAULT false,
    published_at timestamp with time zone,
    published_by character varying(255),
    CONSTRAINT obu_firmware_images_pkey PRIMARY KEY (obu_firmware_id),
    CONSTRAINT obu_firmware_images_manufacturer_fkey FOREIGN KEY (manufacturer)
        REFERENCES public.manufacturers (manufacturer_id)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT obu_firmware_images_verified_upload_fkey FOREIGN KEY (verified_upload_id)
        REFERENCES public.obu_firmware_uploads (upload_id)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT obu_firmware_images_verified_upload_unique UNIQUE (verified_upload_id),
    CONSTRAINT obu_firmware_images_manufacturer_file_name_unique UNIQUE (manufacturer, file_name),
    CONSTRAINT obu_firmware_images_destination_unique UNIQUE (storage_provider, storage_container, object_name),
    CONSTRAINT obu_firmware_images_device_type_valid CHECK (device_type IN ('OBU', 'RSU')),
    CONSTRAINT obu_firmware_images_content_length_positive CHECK (content_length IS NULL OR content_length > 0),
    -- Cloud files are only registered after verification, so they always carry the
    -- evidence used to detect later changes. Local files are verified by the OTA server.
    CONSTRAINT obu_firmware_images_cloud_evidence CHECK (storage_provider = 'local'
        OR (content_length IS NOT NULL AND checksum_algorithm IS NOT NULL AND checksum IS NOT NULL
            AND provider_object_version IS NOT NULL)),
    CONSTRAINT obu_firmware_images_publication_consistent CHECK (published = (published_at IS NOT NULL))
);

CREATE INDEX idx_obu_firmware_images_published
    ON public.obu_firmware_images (manufacturer)
    WHERE published;

COMMENT ON TABLE public.obu_firmware_uploads IS
    'Tracks direct-to-object-storage OBU firmware uploads from signed URL creation through checksum verification.';
COMMENT ON TABLE public.obu_firmware_images IS
    'OBU firmware available to the OBU OTA server. Only published rows are offered to OBUs.';
COMMENT ON COLUMN public.obu_firmware_images.file_name IS
    'Original vendor file name. Must match the manufacturer OTA naming convention and is served verbatim to OBUs.';
COMMENT ON COLUMN public.obu_firmware_images.published IS
    'Whether the OBU OTA server lists this firmware in its manifest.';

COMMIT;
