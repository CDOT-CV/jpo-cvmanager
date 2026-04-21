-- Update public.email_type table definition
-- omit NOT NULL constraint on required_role for now to allow for smooth transition, will set to NOT NULL after backfilling data
ALTER TABLE public.email_type
ADD COLUMN IF NOT EXISTS required_role integer;

-- Add foreign key constraint
ALTER TABLE public.email_type
ADD CONSTRAINT IF NOT EXISTS fk_role_id FOREIGN KEY (required_role)
   REFERENCES public.roles (role_id) MATCH SIMPLE
   ON UPDATE NO ACTION
   ON DELETE NO ACTION;

-- Set default value for all existing entries to role_id 1 (ADMIN)
UPDATE public.email_type
SET required_role = 1
WHERE required_role IS NULL;

-- ADMIN roles
UPDATE public.email_type
SET required_role = 1
WHERE email_type IN ('Support Requests', 'Access Requests');

-- OPERATOR roles
UPDATE public.email_type
SET required_role = 2
WHERE email_type IN ('Firmware Upgrade Failures', 'Critical Error Messages');

-- USER roles
UPDATE public.email_type
SET required_role = 3
WHERE email_type IN ('Daily Message Counts', 'Intersection Notification Summary');

-- Make the column NOT NULL after setting all values
ALTER TABLE public.email_type
ALTER COLUMN required_role SET NOT NULL;
