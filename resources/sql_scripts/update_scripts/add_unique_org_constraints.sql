IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'user_organization_unique') THEN
    ALTER TABLE public.user_organization ADD CONSTRAINT user_organization_unique UNIQUE (user_id, organization_id);
END IF;

IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'rsu_organization_unique') THEN
    ALTER TABLE public.rsu_organization ADD CONSTRAINT rsu_organization_unique UNIQUE (rsu_id, organization_id);
END IF;

IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'intersection_organization_unique') THEN
    ALTER TABLE public.intersection_organization ADD CONSTRAINT intersection_organization_unique UNIQUE (intersection_id, organization_id);
END IF;