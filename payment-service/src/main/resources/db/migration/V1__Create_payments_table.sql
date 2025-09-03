-- Create payments table
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    payment_number VARCHAR(50) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    method VARCHAR(30) NOT NULL,
    
    -- Stripe-specific fields
    stripe_payment_intent_id VARCHAR(100) UNIQUE,
    stripe_customer_id VARCHAR(100),
    stripe_payment_method_id VARCHAR(100),
    stripe_response TEXT,
    
    -- Additional payment details
    description VARCHAR(500),
    failure_reason VARCHAR(500),
    receipt_email VARCHAR(100),
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- Create indexes for payments table
CREATE INDEX idx_payment_number ON payments(payment_number);
CREATE INDEX idx_order_id ON payments(order_id);
CREATE INDEX idx_user_id ON payments(user_id);
CREATE INDEX idx_status ON payments(status);
CREATE INDEX idx_stripe_payment_intent_id ON payments(stripe_payment_intent_id);
CREATE INDEX idx_stripe_customer_id ON payments(stripe_customer_id);
CREATE INDEX idx_created_at ON payments(created_at);

-- Add constraints
ALTER TABLE payments ADD CONSTRAINT chk_amount_positive CHECK (amount > 0);
ALTER TABLE payments ADD CONSTRAINT chk_currency_valid CHECK (currency IN ('USD', 'EUR', 'VND', 'GBP', 'JPY', 'SGD', 'AUD', 'CAD'));
ALTER TABLE payments ADD CONSTRAINT chk_status_valid CHECK (status IN ('PENDING', 'REQUIRES_ACTION', 'REQUIRES_CONFIRMATION', 'REQUIRES_PAYMENT_METHOD', 'SUCCEEDED', 'CANCELED', 'FAILED', 'PROCESSING'));
ALTER TABLE payments ADD CONSTRAINT chk_method_valid CHECK (method IN ('CARD', 'BANK_ACCOUNT', 'WALLET', 'BUY_NOW_PAY_LATER', 'BANK_TRANSFER', 'OTHER'));

-- Create trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_payments_updated_at BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
