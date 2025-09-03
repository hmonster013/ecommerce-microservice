-- Additional indexes and constraints for performance optimization

-- Composite indexes for common query patterns
CREATE INDEX idx_payments_user_status ON payments(user_id, status);
CREATE INDEX idx_payments_order_status ON payments(order_id, status);
CREATE INDEX idx_payments_status_created_at ON payments(status, created_at);
CREATE INDEX idx_payments_user_created_at ON payments(user_id, created_at DESC);

-- Payment methods composite indexes
CREATE INDEX idx_payment_methods_user_type ON payment_methods(user_id, type);
CREATE INDEX idx_payment_methods_user_active_created ON payment_methods(user_id, is_active, created_at DESC);

-- Payment transactions composite indexes
CREATE INDEX idx_payment_transactions_payment_type ON payment_transactions(payment_id, type);
CREATE INDEX idx_payment_transactions_payment_status ON payment_transactions(payment_id, status);
CREATE INDEX idx_payment_transactions_status_created_at ON payment_transactions(status, created_at);

-- Refunds composite indexes
CREATE INDEX idx_refunds_payment_status ON refunds(payment_id, status);
CREATE INDEX idx_refunds_order_status ON refunds(order_id, status);
CREATE INDEX idx_refunds_status_created_at ON refunds(status, created_at);

-- Partial indexes for active records
CREATE INDEX idx_payment_methods_active ON payment_methods(user_id, created_at DESC) WHERE is_active = TRUE;
CREATE INDEX idx_payment_methods_default ON payment_methods(user_id) WHERE is_default = TRUE AND is_active = TRUE;

-- Indexes for Stripe-specific queries
CREATE INDEX idx_payments_stripe_customer ON payments(stripe_customer_id) WHERE stripe_customer_id IS NOT NULL;
CREATE INDEX idx_payment_methods_stripe_customer ON payment_methods(stripe_customer_id) WHERE stripe_customer_id IS NOT NULL;

-- Performance indexes for reporting queries
CREATE INDEX idx_payments_created_at_amount ON payments(created_at, amount) WHERE status = 'SUCCEEDED';
CREATE INDEX idx_refunds_created_at_amount ON refunds(created_at, amount) WHERE status = 'SUCCEEDED';

-- Add additional business constraints

-- Ensure refund amount doesn't exceed payment amount
-- This will be enforced at application level due to complexity of partial refunds

-- Add check constraint for payment method expiry
ALTER TABLE payment_methods ADD CONSTRAINT chk_payment_methods_card_expiry 
    CHECK (
        (type != 'CARD') OR 
        (expiry_month IS NOT NULL AND expiry_year IS NOT NULL AND expiry_year >= EXTRACT(YEAR FROM CURRENT_DATE))
    );

-- Add constraint to ensure Stripe payment methods have Stripe IDs
ALTER TABLE payment_methods ADD CONSTRAINT chk_payment_methods_stripe_id 
    CHECK (
        (provider != 'STRIPE') OR 
        (stripe_payment_method_id IS NOT NULL)
    );

-- Add constraint to ensure successful payments have Stripe payment intent ID
ALTER TABLE payments ADD CONSTRAINT chk_payments_stripe_intent_id 
    CHECK (
        (status != 'SUCCEEDED') OR 
        (stripe_payment_intent_id IS NOT NULL)
    );

-- Create function to validate refund amount against payment
CREATE OR REPLACE FUNCTION validate_refund_amount()
RETURNS TRIGGER AS $$
DECLARE
    payment_amount DECIMAL(19,2);
    total_refunded DECIMAL(19,2);
BEGIN
    -- Get payment amount
    SELECT amount INTO payment_amount 
    FROM payments 
    WHERE id = NEW.payment_id;
    
    -- Calculate total refunded amount (excluding current refund if updating)
    SELECT COALESCE(SUM(amount), 0) INTO total_refunded
    FROM refunds 
    WHERE payment_id = NEW.payment_id 
    AND status = 'SUCCEEDED'
    AND (TG_OP = 'INSERT' OR id != NEW.id);
    
    -- Check if total refund would exceed payment amount
    IF (total_refunded + NEW.amount) > payment_amount THEN
        RAISE EXCEPTION 'Total refund amount (%) would exceed payment amount (%)', 
            (total_refunded + NEW.amount), payment_amount;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to validate refund amounts
CREATE TRIGGER validate_refund_amount_trigger
    BEFORE INSERT OR UPDATE ON refunds
    FOR EACH ROW
    WHEN (NEW.status = 'SUCCEEDED' OR NEW.status = 'PENDING')
    EXECUTE FUNCTION validate_refund_amount();
