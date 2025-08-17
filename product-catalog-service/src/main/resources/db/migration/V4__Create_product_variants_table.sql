-- Create variant type enum
CREATE TYPE variant_type AS ENUM ('SIZE', 'COLOR', 'MATERIAL', 'STYLE', 'CAPACITY', 'WEIGHT', 'DIMENSION', 'FLAVOR', 'SCENT', 'PATTERN', 'FINISH', 'CUSTOM');

-- Create product_variants table
CREATE TABLE product_variants (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    variant_type variant_type NOT NULL,
    name VARCHAR(255) NOT NULL,
    value VARCHAR(255) NOT NULL,
    price_adjustment DECIMAL(10,2) DEFAULT 0.00 CHECK (price_adjustment >= 0),
    sku VARCHAR(100) UNIQUE,
    display_order INTEGER DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    image_url VARCHAR(255),
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    
    CONSTRAINT fk_product_variants_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX idx_product_variants_product_id ON product_variants(product_id);
CREATE INDEX idx_product_variants_sku ON product_variants(sku);
CREATE INDEX idx_product_variants_type ON product_variants(variant_type);
CREATE INDEX idx_product_variants_active ON product_variants(is_active);
CREATE INDEX idx_product_variants_display_order ON product_variants(display_order);

-- Create trigger to update updated_at
CREATE TRIGGER update_product_variants_updated_at 
    BEFORE UPDATE ON product_variants 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();
