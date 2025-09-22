-- Create product_variants table
CREATE TABLE product_variants (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    variant_type VARCHAR(20) NOT NULL CHECK (variant_type IN ('SIZE', 'COLOR', 'MATERIAL', 'STYLE', 'CAPACITY', 'WEIGHT', 'DIMENSION', 'FLAVOR', 'SCENT', 'PATTERN', 'FINISH', 'CUSTOM')),
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

-- Create trigger to update updated_at
CREATE TRIGGER update_product_variants_updated_at 
    BEFORE UPDATE ON product_variants 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();
