-- Create products table
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    short_description VARCHAR(500),
    sku VARCHAR(100) NOT NULL UNIQUE,
    price DECIMAL(10,2) NOT NULL CHECK (price >= 0),
    compare_price DECIMAL(10,2) CHECK (compare_price >= 0),
    cost_price DECIMAL(10,2) CHECK (cost_price >= 0),
    brand VARCHAR(255),
    weight DECIMAL(8,3) CHECK (weight >= 0),
    dimensions VARCHAR(100), -- e.g., "10x20x30 cm"
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'DISCONTINUED', 'OUT_OF_STOCK')),
    is_featured BOOLEAN NOT NULL DEFAULT false,
    is_digital BOOLEAN NOT NULL DEFAULT false,
    requires_shipping BOOLEAN NOT NULL DEFAULT true,
    meta_title VARCHAR(255),
    meta_description TEXT,
    search_keywords TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

-- Create trigger to update updated_at
CREATE TRIGGER update_products_updated_at 
    BEFORE UPDATE ON products 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();
