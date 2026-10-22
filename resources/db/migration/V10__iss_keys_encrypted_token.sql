-- Fernet ciphertext exceeds the previous 128-character token limit.
ALTER TABLE public.iss_keys
    ALTER COLUMN token TYPE text;

-- Keep the latest token for the first refresh, then enforce one current token.
DELETE FROM public.iss_keys
WHERE iss_key_id <> (SELECT max(iss_key_id) FROM public.iss_keys);

CREATE UNIQUE INDEX iss_keys_singleton ON public.iss_keys ((true));

COMMENT ON TABLE public.iss_keys IS
    'Encrypted ISS SCMS API token used by the iss_health_check service to query certificate status on behalf of RSUs.';
