-- alter rsu_credentials table
ALTER TABLE public.rsu_credentials
    ADD COLUMN owner_organization_id INTEGER,
    ADD CONSTRAINT fk_owner_organization_id FOREIGN KEY (owner_organization_id)
        REFERENCES public.organizations (organization_id);

UPDATE public.rsu_credentials rc
SET owner_organization_id = (
    SELECT ro.organization_id
    FROM public.rsu_organization ro
    JOIN public.rsus r ON ro.rsu_id = r.rsu_id
    WHERE r.credential_id = rc.credential_id
    ORDER BY r.rsu_id ASC
    LIMIT 1
);

DELETE FROM public.rsu_credentials
WHERE owner_organization_id IS NULL;

ALTER TABLE public.rsu_credentials
    ALTER COLUMN owner_organization_id SET NOT NULL;


-- alter snmp_credentials table
ALTER TABLE public.snmp_credentials
    ADD COLUMN owner_organization_id INTEGER,
    ADD CONSTRAINT fk_owner_organization_id FOREIGN KEY (owner_organization_id)
        REFERENCES public.organizations (organization_id);

UPDATE public.snmp_credentials sc
SET owner_organization_id = (
    SELECT ro.organization_id
    FROM public.rsu_organization ro
    JOIN public.rsus r ON ro.rsu_id = r.rsu_id
    WHERE r.snmp_credential_id = sc.snmp_credential_id
    ORDER BY r.rsu_id ASC
    LIMIT 1
);

DELETE FROM public.snmp_credentials
WHERE owner_organization_id IS NULL;

ALTER TABLE public.snmp_credentials
    ALTER COLUMN owner_organization_id SET NOT NULL;