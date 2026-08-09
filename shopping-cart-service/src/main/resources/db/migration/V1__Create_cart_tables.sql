-- Create shopping cart tables
CREATE TABLE carts (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36),
    session_id VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    cart_type VARCHAR(20) NOT NULL DEFAULT 'GUEST',
    expires_at TIMESTAMP,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    subtotal DECIMAL(19,2) DEFAULT 0.00,
    tax_amount DECIMAL(19,2) DEFAULT 0.00,
    shipping_amount DECIMAL(19,2) DEFAULT 0.00,
    discount_amount DECIMAL(19,2) DEFAULT 0.00,
    total_amount DECIMAL(19,2) DEFAULT 0.00,
    item_count INTEGER NOT NULL DEFAULT 0,
    total_quantity INTEGER NOT NULL DEFAULT 0,
    notes VARCHAR(1000),
    coupon_code VARCHAR(100),
    last_activity_at TIMESTAMP,
    checkout_started_at TIMESTAMP,
    converted_to_order_id VARCHAR(36),
    merged_to_cart_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT,
    deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(100)
);

CREATE TABLE cart_items (
    id BIGSERIAL PRIMARY KEY,
    cart_id BIGINT NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    product_sku VARCHAR(100),
    product_name VARCHAR(255) NOT NULL,
    product_description VARCHAR(1000),
    product_image_url VARCHAR(500),
    category_id VARCHAR(36),
    category_name VARCHAR(255),
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(19,2) NOT NULL,
    original_price DECIMAL(19,2),
    discount_amount DECIMAL(19,2) DEFAULT 0.00,
    total_price DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    weight DECIMAL(10,3),
    dimensions VARCHAR(100),
    variant_id VARCHAR(36),
    variant_attributes VARCHAR(1000),
    special_instructions VARCHAR(500),
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_price_check_at TIMESTAMP,
    price_changed BOOLEAN NOT NULL DEFAULT false,
    availability_status VARCHAR(20) DEFAULT 'AVAILABLE',
    stock_quantity INTEGER,
    max_quantity_per_order INTEGER,
    is_gift BOOLEAN NOT NULL DEFAULT false,
    gift_message VARCHAR(500),
    gift_wrap_type VARCHAR(50),
    gift_wrap_price DECIMAL(19,2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT,
    deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(100),

    CONSTRAINT fk_cart_item_cart FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE
);

CREATE INDEX idx_cart_user_id ON carts(user_id);
CREATE INDEX idx_cart_session_id ON carts(session_id);
CREATE INDEX idx_cart_status ON carts(status);
CREATE INDEX idx_cart_type ON carts(cart_type);
CREATE INDEX idx_cart_expires_at ON carts(expires_at);
CREATE INDEX idx_cart_created_at ON carts(created_at);
CREATE INDEX idx_cart_user_status ON carts(user_id, status);
CREATE INDEX idx_cart_session_status ON carts(session_id, status);

CREATE INDEX idx_cart_item_cart_id ON cart_items(cart_id);
CREATE INDEX idx_cart_item_product_id ON cart_items(product_id);
CREATE INDEX idx_cart_item_cart_product ON cart_items(cart_id, product_id);
CREATE INDEX idx_cart_item_created_at ON cart_items(created_at);
