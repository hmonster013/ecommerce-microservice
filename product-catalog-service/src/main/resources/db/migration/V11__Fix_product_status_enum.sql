-- Fix product status enum compatibility with Hibernate
-- Change from custom enum type to VARCHAR with check constraint

-- First, alter the column to use VARCHAR with the same values
ALTER TABLE products 
ALTER COLUMN status TYPE VARCHAR(20) USING status::text;

-- Add check constraint to ensure only valid values
ALTER TABLE products 
ADD CONSTRAINT products_status_check 
CHECK (status IN ('ACTIVE', 'INACTIVE', 'DISCONTINUED', 'OUT_OF_STOCK'));

-- Drop the custom enum type (optional, but clean up)
-- Note: This might fail if other tables use this type
-- DROP TYPE IF EXISTS product_status;

-- Update any existing NULL values to default
UPDATE products SET status = 'ACTIVE' WHERE status IS NULL;

-- Ensure the column is NOT NULL
ALTER TABLE products ALTER COLUMN status SET NOT NULL;
