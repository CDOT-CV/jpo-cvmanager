-- alter rsu_credentials table
ALTER TABLE public.rsu_credentials
    ADD COLUMN owner_organization_id INTEGER,
    ADD CONSTRAINT fk_owner_organization_id FOREIGN KEY (owner_organization_id)
        REFERENCES public.organizations (organization_id);

DO
$do$
    BEGIN
        FOR i in 1..(SELECT count(*) FROM public.rsu_credentials)
            LOOP
                UPDATE public.rsu_credentials
                SET owner_organization_id = (SELECT organization_id
                                             FROM public.rsu_organization
                                             WHERE rsu_id IN
                                                   (SELECT rsu_id FROM public.rsus WHERE credential_id = i LIMIT 1))
                where credential_id = i;
            END LOOP;
    END
$do$;

ALTER TABLE public.rsu_credentials
    ALTER COLUMN owner_organization_id SET NOT NULL;


-- alter snmp_credentials table
ALTER TABLE public.snmp_credentials
    ADD COLUMN owner_organization_id INTEGER,
    ADD CONSTRAINT fk_owner_organization_id FOREIGN KEY (owner_organization_id)
        REFERENCES public.organizations (organization_id);

DO
$do$
    BEGIN
        FOR i in 1..(SELECT count(*) FROM public.snmp_credentials)
            LOOP
                UPDATE public.snmp_credentials
                SET owner_organization_id = (SELECT organization_id
                                             FROM public.rsu_organization
                                             WHERE rsu_id IN
                                                   (SELECT rsu_id FROM public.rsus WHERE snmp_credential_id = i LIMIT 1))
                where snmp_credential_id = i;
            END LOOP;
    END
$do$;

ALTER TABLE public.snmp_credentials
    ALTER COLUMN owner_organization_id SET NOT NULL;