-- Create variant_inventory table
CREATE TABLE variant_inventory (
    id BIGSERIAL PRIMARY KEY,
    variant_id BIGINT NOT NULL UNIQUE,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    reserved_quantity INTEGER NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    min_stock_level INTEGER DEFAULT 0 CHECK (min_stock_level >= 0),
    max_stock_level INTEGER CHECK (max_stock_level >= 0),
    reorder_point INTEGER DEFAULT 0 CHECK (reorder_point >= 0),
    reorder_quantity INTEGER CHECK (reorder_quantity >= 0),
    track_inventory BOOLEAN NOT NULL DEFAULT true,
    allow_backorder BOOLEAN NOT NULL DEFAULT false,
    location VARCHAR(255),
    sku VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    
    CONSTRAINT fk_variant_inventory_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE CASCADE,
    CONSTRAINT fk_variant_inventory_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT chk_variant_inventory_reserved_quantity CHECK (reserved_quantity <= quantity),
    CONSTRAINT chk_variant_inventory_max_stock CHECK (max_stock_level IS NULL OR max_stock_level >= min_stock_level)
);

-- Indexes are defined in JPA entity @Index annotations

-- Create trigger to update updated_at
CREATE TRIGGER update_variant_inventory_updated_at 
    BEFORE UPDATE ON variant_inventory 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Create function to automatically create variant inventory record when variant is created
CREATE OR REPLACE FUNCTION create_default_variant_inventory()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO variant_inventory (variant_id, product_id, quantity, reserved_quantity, min_stock_level, track_inventory, allow_backorder)
    VALUES (NEW.id, NEW.product_id, 0, 0, 0, true, false);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to auto-create variant inventory for new variants
CREATE TRIGGER trigger_create_default_variant_inventory
    AFTER INSERT ON product_variants
    FOR EACH ROW
    EXECUTE FUNCTION create_default_variant_inventory();
