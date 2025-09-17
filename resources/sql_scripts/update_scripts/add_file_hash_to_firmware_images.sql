-- Add file_hash column to firmware_images table
-- This script adds the file_hash column to existing firmware_images tables
-- Run this script if you have an existing CV Manager PostgreSQL database

-- Add file_hash column (nullable to allow existing records)
ALTER TABLE public.firmware_images 
ADD COLUMN IF NOT EXISTS file_hash character varying(128) COLLATE pg_catalog.default;

-- Add comment to document the column
COMMENT ON COLUMN public.firmware_images.file_hash IS 'SHA-256 hash of the firmware file for integrity verification';

-- Optional: Create an index on file_hash for faster lookups (uncomment if needed)
-- CREATE INDEX IF NOT EXISTS idx_firmware_images_file_hash ON public.firmware_images(file_hash);
