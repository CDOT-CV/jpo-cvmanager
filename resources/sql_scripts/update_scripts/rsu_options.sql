-- Add the rsu_options table
CREATE TABLE IF NOT EXISTS public.rsu_options (
    rsu_id integer NOT NULL,
    tim_deposit boolean NOT NULL DEFAULT TRUE,
    snmp_monitoring boolean NOT NULL DEFAULT FALSE,
    CONSTRAINT rsu_options_pkey PRIMARY KEY (rsu_id),
    CONSTRAINT fk_rsu_id FOREIGN KEY (rsu_id)
        REFERENCES public.rsus (rsu_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);

-- Populate existing rows for tim_deposit
-- Populate with '0' UNLESS they belong to the "Region 1" organization
INSERT INTO public.rsu_options (rsu_id, tim_deposit, snmp_monitoring)
SELECT rsu_id, TRUE, TRUE FROM public.rsus
ON CONFLICT (rsu_id) DO NOTHING;

UPDATE public.rsu_options SET tim_deposit = FALSE 
WHERE rsu_id IN (
    SELECT rsu_id 
    FROM public.rsu_organization_name 
    WHERE name = 'Region 1'
);