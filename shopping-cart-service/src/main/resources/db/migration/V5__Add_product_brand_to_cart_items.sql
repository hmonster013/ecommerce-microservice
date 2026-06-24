-- Migration V5: Add product_brand column to cart_items table

-- Add the product_brand column
ALTER TABLE cart_items ADD COLUMN IF NOT EXISTS product_brand VARCHAR(100);

-- Add comment to track the change
COMMENT ON COLUMN cart_items.product_brand IS 'Product brand from catalog service';
