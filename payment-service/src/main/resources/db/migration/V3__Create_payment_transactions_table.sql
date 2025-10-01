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

-- Indexes are managed by JPA @Index annotations in PaymentTransaction entity

-- Add business constraints
ALTER TABLE payment_transactions ADD CONSTRAINT chk_payment_transactions_amount_positive CHECK (amount > 0);

-- Create trigger to update updated_at timestamp
CREATE TRIGGER update_payment_transactions_updated_at BEFORE UPDATE ON payment_transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
