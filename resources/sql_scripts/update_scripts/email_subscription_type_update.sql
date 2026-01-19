-- user_email_notification table changes
-- Add new columns to email_type table
ALTER TABLE public.email_type
  ADD COLUMN IF NOT EXISTS description character varying(256),
  ADD COLUMN IF NOT EXISTS supports_immediate boolean DEFAULT true NOT NULL,
  ADD COLUMN IF NOT EXISTS supports_hourly boolean DEFAULT false NOT NULL,
  ADD COLUMN IF NOT EXISTS supports_daily boolean DEFAULT false NOT NULL,
  ADD COLUMN IF NOT EXISTS supports_weekly boolean DEFAULT false NOT NULL,
  ADD COLUMN IF NOT EXISTS supports_monthly boolean DEFAULT false NOT NULL;

-- Add constraints to email_type
ALTER TABLE public.email_type
  ADD CONSTRAINT IF NOT EXISTS email_type_unique UNIQUE (email_type),
  ADD CONSTRAINT IF NOT EXISTS at_least_one_frequency 
    CHECK (supports_immediate OR supports_hourly OR supports_daily OR supports_weekly OR supports_monthly);


-- Insert or update email types with specific frequency settings
INSERT INTO public.email_type(email_type, supports_immediate, supports_hourly, supports_daily, supports_weekly, supports_monthly)
VALUES 
  ('Support Requests', true, false, false, false, false), 
  ('Firmware Upgrade Failures', true, false, false, false, false), 
  ('Daily Message Counts', true, false, false, false, false), 
  ('Access Requests', true, false, false, false, false), 
  ('Intersection Notification Summary', true, true, true, true, true), 
  ('Critical Error Messages', true, false, false, false, false)
ON CONFLICT (email_type) 
DO UPDATE SET
  supports_immediate = EXCLUDED.supports_immediate,
  supports_hourly = EXCLUDED.supports_hourly,
  supports_daily = EXCLUDED.supports_daily,
  supports_weekly = EXCLUDED.supports_weekly,
  supports_monthly = EXCLUDED.supports_monthly;



-- Add new columns to user_email_notification table
ALTER TABLE public.user_email_notification
  ADD COLUMN IF NOT EXISTS immediate boolean DEFAULT true NOT NULL,
  ADD COLUMN IF NOT EXISTS hourly boolean DEFAULT false NOT NULL,
  ADD COLUMN IF NOT EXISTS daily boolean DEFAULT false NOT NULL,
  ADD COLUMN IF NOT EXISTS weekly boolean DEFAULT false NOT NULL,
  ADD COLUMN IF NOT EXISTS monthly boolean DEFAULT false NOT NULL;

-- Add constraints to user_email_notification
ALTER TABLE public.user_email_notification
  ADD CONSTRAINT IF NOT EXISTS user_email_notification_unique UNIQUE (user_id, email_type_id),
  ADD CONSTRAINT IF NOT EXISTS at_least_one_subscription 
    CHECK (immediate OR hourly OR daily OR weekly OR monthly);





