-- Convert bit(1) columns to boolean type
ALTER TABLE public.ping 
ALTER COLUMN result TYPE boolean 
USING CASE WHEN result = B'1' THEN true ELSE false END;

ALTER TABLE public.users 
ALTER COLUMN super_user TYPE boolean 
USING CASE WHEN super_user = B'1' THEN true ELSE false END;

ALTER TABLE public.users 
ALTER COLUMN super_user SET DEFAULT false;

ALTER TABLE public.scms_health 
ALTER COLUMN health TYPE boolean 
USING CASE WHEN health = B'1' THEN true ELSE false END;

ALTER TABLE public.snmp_msgfwd_config 
ALTER COLUMN active TYPE boolean 
USING CASE WHEN active = B'1' THEN true ELSE false END;

ALTER TABLE public.snmp_msgfwd_config 
ALTER COLUMN security TYPE boolean 
USING CASE WHEN security = B'1' THEN true ELSE false END;

ALTER TABLE public.obu_ota_requests 
ALTER COLUMN error_status TYPE boolean 
USING CASE WHEN error_status = B'1' THEN true ELSE false END;

-- Add missing primary key constraints
ALTER TABLE public.obu_ota_requests 
ADD CONSTRAINT obu_ota_requests_pkey PRIMARY KEY (request_id);

ALTER TABLE public.iss_keys 
ADD CONSTRAINT iss_keys_pkey PRIMARY KEY (iss_key_id);