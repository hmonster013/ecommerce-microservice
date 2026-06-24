-- V1__baseline_order_schema.sql
-- Baseline schema for order-service

CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(50) NOT NULL UNIQUE,
    user_id VARCHAR(36) NOT NULL,
    status VARCHAR(30) NOT NULL,
    order_type VARCHAR(20) NOT NULL,
    
    total_amount NUMERIC(19, 4),
    currency VARCHAR(3),
    
    subtotal_amount NUMERIC(19, 4),
    subtotal_currency VARCHAR(3),
    
    tax_amount NUMERIC(19, 4),
    tax_currency VARCHAR(3),
    
    shipping_amount NUMERIC(19, 4),
    shipping_currency VARCHAR(3),
    
    discount_amount NUMERIC(19, 4),
    discount_currency VARCHAR(3),
    
    shipping_first_name VARCHAR(255),
    shipping_last_name VARCHAR(255),
    shipping_company VARCHAR(255),
    shipping_street_address VARCHAR(255),
    shipping_street_address_2 VARCHAR(255),
    shipping_city VARCHAR(255),
    shipping_state VARCHAR(255),
    shipping_postal_code VARCHAR(255),
    shipping_country VARCHAR(255),
    shipping_phone VARCHAR(255),
    shipping_email VARCHAR(255),
    shipping_delivery_instructions VARCHAR(255),
    shipping_address_type VARCHAR(255),
    shipping_is_residential BOOLEAN,
    
    billing_first_name VARCHAR(255),
    billing_last_name VARCHAR(255),
    billing_company VARCHAR(255),
    billing_street_address VARCHAR(255),
    billing_street_address_2 VARCHAR(255),
    billing_city VARCHAR(255),
    billing_state VARCHAR(255),
    billing_postal_code VARCHAR(255),
    billing_country VARCHAR(255),
    billing_phone VARCHAR(255),
    billing_email VARCHAR(255),
    billing_delivery_instructions VARCHAR(255),
    billing_address_type VARCHAR(255),
    billing_is_residential BOOLEAN,
    
    customer_notes VARCHAR(2000),
    internal_notes VARCHAR(2000),
    order_source VARCHAR(20),
    expected_delivery_date TIMESTAMP,
    actual_delivery_date TIMESTAMP,
    confirmed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancellation_reason VARCHAR(500),
    priority_level INTEGER,
    requires_special_handling BOOLEAN,
    is_gift BOOLEAN,
    gift_message VARCHAR(1000),
    
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_order_number ON orders (order_number);
CREATE INDEX IF NOT EXISTS idx_user_id ON orders (user_id);
CREATE INDEX IF NOT EXISTS idx_status ON orders (status);
CREATE INDEX IF NOT EXISTS idx_order_type ON orders (order_type);
CREATE INDEX IF NOT EXISTS idx_created_at ON orders (created_at);
CREATE INDEX IF NOT EXISTS idx_user_status ON orders (user_id, status);
CREATE INDEX IF NOT EXISTS idx_status_created ON orders (status, created_at);

CREATE TABLE IF NOT EXISTS order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    sku VARCHAR(100) NOT NULL,
    product_name VARCHAR(500) NOT NULL,
    product_description VARCHAR(2000),
    product_category VARCHAR(200),
    product_brand VARCHAR(200),
    quantity INTEGER NOT NULL,
    
    unit_price NUMERIC(19, 4),
    unit_price_currency VARCHAR(3),
    
    total_price NUMERIC(19, 4),
    total_price_currency VARCHAR(3),
    
    discount_amount NUMERIC(19, 4),
    discount_currency VARCHAR(3),
    
    tax_amount NUMERIC(19, 4),
    tax_currency VARCHAR(3),
    
    weight NUMERIC(10, 3),
    weight_unit VARCHAR(10),
    dimensions VARCHAR(100),
    product_image_url VARCHAR(1000),
    variant_info VARCHAR(1000),
    special_instructions VARCHAR(1000),
    is_gift BOOLEAN,
    gift_wrap_type VARCHAR(100),
    gift_message VARCHAR(500),
    requires_special_handling BOOLEAN,
    is_fragile BOOLEAN,
    is_hazardous BOOLEAN,
    expected_delivery_date TIMESTAMP,
    actual_delivery_date TIMESTAMP,
    status VARCHAR(30),
    
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP,
    
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items (order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_product_id ON order_items (product_id);
CREATE INDEX IF NOT EXISTS idx_order_items_order_product ON order_items (order_id, product_id);
CREATE INDEX IF NOT EXISTS idx_order_items_sku ON order_items (sku);
