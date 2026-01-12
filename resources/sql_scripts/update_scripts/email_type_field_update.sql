-- Update public.users table definition
ALTER TABLE public.email_type
ADD COLUMN IF NOT EXISTS description character varying(256);