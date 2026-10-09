BEGIN;

-- Existing deployments chose destinations manually. Do not choose between
-- competing paths on their behalf when enforcing one destination per source.
DO $$
DECLARE
    ambiguous_sources text;
BEGIN
    SELECT string_agg(format('source=%s, destinations=%s', from_id, destinations), '; ' ORDER BY from_id)
    INTO ambiguous_sources
    FROM (
        SELECT from_id, array_agg(to_id ORDER BY to_id) AS destinations
        FROM public.firmware_upgrade_rules
        GROUP BY from_id HAVING count(*) > 1
    ) duplicates;
    IF ambiguous_sources IS NOT NULL THEN
        RAISE EXCEPTION USING
            MESSAGE = 'Multiple firmware upgrade destinations exist for the same source',
            DETAIL = ambiguous_sources,
            HINT = 'Keep the intended destination for each listed source and retry the migration.';
    END IF;
END $$;

ALTER TABLE public.firmware_upgrade_rules
    ADD CONSTRAINT firmware_upgrade_rules_source_unique UNIQUE (from_id);

COMMIT;
