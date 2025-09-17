-- Add manufacturer_id column to firmware_images table
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'firmware_images' AND column_name = 'manufacturer_id') THEN
        -- Add manufacturer_id column
        ALTER TABLE public.firmware_images
        ADD COLUMN manufacturer_id integer;
        
        -- Add foreign key constraint
        ALTER TABLE public.firmware_images
        ADD CONSTRAINT fk_manufacturer FOREIGN KEY (manufacturer_id)
            REFERENCES public.manufacturers (manufacturer_id) MATCH SIMPLE
            ON UPDATE NO ACTION
            ON DELETE NO ACTION;
        
        -- Update existing records to use manufacturer from rsu_models table
        UPDATE public.firmware_images 
        SET manufacturer_id = rm.manufacturer
        FROM public.rsu_models rm
        WHERE firmware_images.model = rm.rsu_model_id;
        
        -- Make the column NOT NULL after populating it
        ALTER TABLE public.firmware_images
        ALTER COLUMN manufacturer_id SET NOT NULL;
        
        COMMENT ON COLUMN public.firmware_images.manufacturer_id IS 'Manufacturer of the firmware';
    END IF;
END
$$;
