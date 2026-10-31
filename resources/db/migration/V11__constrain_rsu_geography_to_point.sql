-- Constrain public.rsus.geography to 2D Points in SRID 4326.
--
-- The column was created as unconstrained "geography", which accepts any
-- geometry type (Polygon, LineString, ...) and any lon/lat SRID. RSUs are
-- only ever located by a single point, and every reader (map display,
-- intersection-api polygon queries, rsu-info-bridge) assumes a Point in
-- WGS 84. The typmod makes the database reject anything else.
--
-- Rollback: Flyway Community has no undo. The change is metadata-only for
-- valid data, so reverting is a new forward migration running
--   ALTER TABLE public.rsus ALTER COLUMN geography TYPE geography;

-- Pre-flight audit. ALTER COLUMN TYPE would fail on the first bad row with a
-- generic typmod error; this lists every offending RSU so operators can fix
-- the data before re-running.
DO $$
DECLARE offending text;
BEGIN
    SELECT string_agg(
               format('rsu_id=%s (type=%s, srid=%s, dims=%s)',
                      rsu_id,
                      ST_GeometryType(geography::geometry),
                      ST_SRID(geography),
                      ST_NDims(geography::geometry)),
               '; ' ORDER BY rsu_id)
    INTO offending
    FROM public.rsus
    WHERE ST_GeometryType(geography::geometry) <> 'ST_Point'
       OR ST_SRID(geography) <> 4326
       OR ST_NDims(geography::geometry) <> 2;

    IF offending IS NOT NULL THEN
        RAISE EXCEPTION 'public.rsus.geography cannot be constrained to geography(Point, 4326). Correct these rows first: %', offending;
    END IF;
END $$;

ALTER TABLE public.rsus
    ALTER COLUMN geography TYPE geography(Point, 4326);

COMMENT ON COLUMN public.rsus.geography IS
    'RSU location as a 2D WGS 84 point (longitude, latitude). Constrained to geography(Point, 4326).';
