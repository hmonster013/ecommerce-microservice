-- Create payment_methods table
CREATE TABLE payment_methods (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    provider VARCHAR(50) NOT NULL DEFAULT 'STRIPE',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    
    -- Stripe-specific fields
    stripe_payment_method_id VARCHAR(100) UNIQUE,
    stripe_customer_id VARCHAR(100),
    
    -- Card details (for display purposes only)
    masked_card_number VARCHAR(20),
    card_brand VARCHAR(20),
    expiry_month INTEGER,
    expiry_year INTEGER,
    card_country VARCHAR(2),
    card_funding VARCHAR(20),
    
    -- Customer info
    customer_name VARCHAR(100),
    billing_address_line1 VARCHAR(200),
    billing_address_line2 VARCHAR(200),
    billing_city VARCHAR(100),
    billing_state VARCHAR(100),
    billing_postal_code VARCHAR(20),
    billing_country VARCHAR(2),
    
    -- Digital wallet info
    wallet_type VARCHAR(30),
    wallet_id VARCHAR(100),
    
    -- Additional metadata
    nickname VARCHAR(50),
    last_used_at TIMESTAMP,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- Indexes are managed by JPA @Index annotations in PaymentMethod entity

-- Add business constraints
ALTER TABLE payment_methods ADD CONSTRAINT chk_payment_methods_expiry_month_valid CHECK (expiry_month IS NULL OR (expiry_month >= 1 AND expiry_month <= 12));
ALTER TABLE payment_methods ADD CONSTRAINT chk_payment_methods_expiry_year_valid CHECK (expiry_year IS NULL OR expiry_year >= 2020);

-- Ensure only one default payment method per user
CREATE UNIQUE INDEX idx_payment_methods_user_default ON payment_methods(user_id) WHERE is_default = TRUE AND is_active = TRUE;

-- Create trigger to update updated_at timestamp
CREATE TRIGGER update_payment_methods_updated_at BEFORE UPDATE ON payment_methods
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
