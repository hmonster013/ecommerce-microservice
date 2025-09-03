-- Create payment_transactions table
CREATE TABLE payment_transactions (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    
    -- Stripe-specific fields
    stripe_charge_id VARCHAR(100) UNIQUE,
    stripe_payment_intent_id VARCHAR(100),
    stripe_transfer_group VARCHAR(100),
    stripe_response TEXT,
    
    -- Transaction details
    description VARCHAR(500),
    failure_reason VARCHAR(500),
    gateway_transaction_id VARCHAR(100),
    gateway_response TEXT,
    
    -- Processing details
    processing_fee DECIMAL(19,4),
    net_amount DECIMAL(19,2),
    currency VARCHAR(3),
    
    -- Timing information
    processed_at TIMESTAMP,
    settled_at TIMESTAMP,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    
    -- Foreign key constraint
    CONSTRAINT fk_payment_transactions_payment_id FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE
);

-- Create indexes for payment_transactions table
CREATE INDEX idx_payment_transactions_payment_id ON payment_transactions(payment_id);
CREATE INDEX idx_payment_transactions_type ON payment_transactions(type);
CREATE INDEX idx_payment_transactions_status ON payment_transactions(status);
CREATE INDEX idx_payment_transactions_stripe_charge_id ON payment_transactions(stripe_charge_id);
CREATE INDEX idx_payment_transactions_stripe_transfer_group ON payment_transactions(stripe_transfer_group);
CREATE INDEX idx_payment_transactions_created_at ON payment_transactions(created_at);

-- Add constraints
ALTER TABLE payment_transactions ADD CONSTRAINT chk_payment_transactions_amount_positive CHECK (amount > 0);
ALTER TABLE payment_transactions ADD CONSTRAINT chk_payment_transactions_type_valid CHECK (type IN ('CHARGE', 'AUTHORIZATION', 'CAPTURE', 'REFUND', 'PARTIAL_REFUND', 'CANCELLATION', 'FAILURE'));
ALTER TABLE payment_transactions ADD CONSTRAINT chk_payment_transactions_status_valid CHECK (status IN ('PENDING', 'REQUIRES_ACTION', 'REQUIRES_CONFIRMATION', 'REQUIRES_PAYMENT_METHOD', 'SUCCEEDED', 'CANCELED', 'FAILED', 'PROCESSING'));
ALTER TABLE payment_transactions ADD CONSTRAINT chk_payment_transactions_currency_valid CHECK (currency IS NULL OR currency IN ('USD', 'EUR', 'VND', 'GBP', 'JPY', 'SGD', 'AUD', 'CAD'));

-- Create trigger to update updated_at timestamp
CREATE TRIGGER update_payment_transactions_updated_at BEFORE UPDATE ON payment_transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
