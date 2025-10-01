-- Additional business constraints and validation logic

-- All indexes are managed by JPA @Index annotations in entity classes
-- See: Payment.java, PaymentMethod.java, PaymentTransaction.java, Refund.java

-- Note: ENUM validation constraints are removed to avoid conflicts when adding new enum values
-- Validation is handled at application level by JPA @Enumerated annotations

-- Create function to validate refund amount against payment
-- Note: This function uses hardcoded enum values ('SUCCEEDED', 'PENDING')
-- If RefundStatus enum changes, this function may need to be updated
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
