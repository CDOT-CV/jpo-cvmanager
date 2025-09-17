-- Add device_type column to firmware_images table
-- This script adds the device_type column to existing firmware_images tables
-- Run this script if you have an existing CV Manager PostgreSQL database

-- Add device_type column with default value 'RSU'
ALTER TABLE public.firmware_images 
ADD COLUMN IF NOT EXISTS device_type character varying(10) COLLATE pg_catalog.default NOT NULL DEFAULT 'RSU';

-- Add check constraint to ensure only valid device types
ALTER TABLE public.firmware_images 
ADD CONSTRAINT IF NOT EXISTS firmware_images_device_type_check 
CHECK (device_type IN ('RSU', 'OBU'));

-- Update existing records to have 'RSU' as device_type (if they don't already)
UPDATE public.firmware_images 
SET device_type = 'RSU' 
WHERE device_type IS NULL OR device_type = '';

-- Add comment to document the column
COMMENT ON COLUMN public.firmware_images.device_type IS 'Device type for the firmware: RSU or OBU';
