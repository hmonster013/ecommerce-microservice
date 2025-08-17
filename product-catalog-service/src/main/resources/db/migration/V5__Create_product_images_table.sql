-- Create image type enum
CREATE TYPE image_type AS ENUM ('MAIN', 'GALLERY', 'THUMBNAIL', 'VARIANT', 'DETAIL', 'LIFESTYLE', 'COMPARISON', 'PACKAGING', 'INSTRUCTION', 'WARRANTY');

-- Create product_images table
CREATE TABLE product_images (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    url VARCHAR(500) NOT NULL,
    alt_text VARCHAR(255),
    image_type image_type NOT NULL DEFAULT 'GALLERY',
    display_order INTEGER DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    title VARCHAR(255),
    description TEXT,
    file_size VARCHAR(100),
    dimensions VARCHAR(50),
    file_format VARCHAR(10),
    variant_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_product_images_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE SET NULL
);

-- Create indexes
CREATE INDEX idx_product_images_product_id ON product_images(product_id);
CREATE INDEX idx_product_images_type ON product_images(image_type);
CREATE INDEX idx_product_images_display_order ON product_images(display_order);
CREATE INDEX idx_product_images_active ON product_images(is_active);
CREATE INDEX idx_product_images_variant_id ON product_images(variant_id);

-- Ensure only one main image per product
CREATE UNIQUE INDEX idx_product_images_main_unique 
    ON product_images(product_id) 
    WHERE image_type = 'MAIN' AND is_active = true;

-- Create trigger to update updated_at
CREATE TRIGGER update_product_images_updated_at 
    BEFORE UPDATE ON product_images 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();
