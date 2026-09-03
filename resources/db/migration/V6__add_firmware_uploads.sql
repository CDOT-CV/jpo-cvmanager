BEGIN;

CREATE TABLE public.firmware_uploads
(
    upload_id uuid NOT NULL,
    model integer NOT NULL,
    version character varying(128) NOT NULL,
    file_name character varying(128) NOT NULL,
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
    provider_object_version text,
    observed_checksum character varying(128),
    CONSTRAINT firmware_uploads_pkey PRIMARY KEY (upload_id),
    CONSTRAINT firmware_uploads_model_fkey FOREIGN KEY (model)
        REFERENCES public.rsu_models (rsu_model_id)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT firmware_uploads_expected_size_positive CHECK (expected_size > 0),
    CONSTRAINT firmware_uploads_status_valid CHECK (status IN ('PENDING', 'VERIFIED'))
);

CREATE INDEX idx_firmware_uploads_model
    ON public.firmware_uploads (model);

CREATE INDEX idx_firmware_uploads_status_expires_at
    ON public.firmware_uploads (status, expires_at);

COMMENT ON TABLE public.firmware_uploads IS
    'Tracks direct-to-object-storage firmware artifacts from signed URL creation through checksum verification.';

COMMIT;
