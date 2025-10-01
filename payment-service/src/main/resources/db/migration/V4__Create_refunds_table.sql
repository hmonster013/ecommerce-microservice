-- Create refunds table
CREATE TABLE refunds (
    id BIGSERIAL PRIMARY KEY,
    refund_number VARCHAR(50) NOT NULL UNIQUE,
    payment_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reason VARCHAR(500),
    
    -- Stripe-specific fields
    stripe_refund_id VARCHAR(100) UNIQUE,
    stripe_charge_id VARCHAR(100),
    stripe_payment_intent_id VARCHAR(100),
    stripe_response TEXT,
    
    -- Refund details
    description VARCHAR(500),
    failure_reason VARCHAR(500),
    receipt_number VARCHAR(100),
    
    -- Processing details
    processing_fee_refunded DECIMAL(19,4),
    net_refund_amount DECIMAL(19,2),
    
    -- Timing information
    processed_at TIMESTAMP,
    settled_at TIMESTAMP,
    expected_arrival_date TIMESTAMP,
    
    -- Refund metadata
    refund_type VARCHAR(30) DEFAULT 'FULL',
    initiated_by VARCHAR(100),
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    
    -- Foreign key constraint
    CONSTRAINT fk_refunds_payment_id FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE
);

-- Indexes are managed by JPA @Index annotations in Refund entity

-- Add business constraints
ALTER TABLE refunds ADD CONSTRAINT chk_refunds_amount_positive CHECK (amount > 0);

-- Create trigger to update updated_at timestamp
CREATE TRIGGER update_refunds_updated_at BEFORE UPDATE ON refunds
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
