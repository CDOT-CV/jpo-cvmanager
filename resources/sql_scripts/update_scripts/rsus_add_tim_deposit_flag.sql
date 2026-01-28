-- Add the tim_deposit column to the rsus table
ALTER TABLE public.rsus
    ADD COLUMN tim_deposit bit(1);

-- Populate existing rows with '1' UNLESS they belong to the "Region 1" organization
UPDATE public.rsus SET tim_deposit = '0' 
WHERE rsu_id IN (
    SELECT rsu_id 
    FROM public.rsu_organization_name 
    WHERE name = 'Region 1'
);
UPDATE public.rsus SET tim_deposit = '1' WHERE tim_deposit IS NULL;
ALTER TABLE public.rsus ALTER COLUMN tim_deposit SET DEFAULT '1';
ALTER TABLE public.rsus ALTER COLUMN tim_deposit SET NOT NULL;