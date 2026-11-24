ALTER TABLE payments ADD COLUMN stripe_intent_livemode boolean;
ALTER TABLE payments ADD COLUMN stripe_intent_created_by_service boolean NOT NULL DEFAULT false;
ALTER TABLE payments ADD CONSTRAINT stripe_intent_provenance_check CHECK (
    NOT stripe_intent_created_by_service OR (stripe_intent_livemode IS NOT NULL AND stripe_payment_intent_id IS NOT NULL AND length(trim(stripe_payment_intent_id)) > 0)
);

ALTER TABLE payment_order_guard DROP CONSTRAINT checkout_closure_state_check;
ALTER TABLE payment_order_guard ALTER COLUMN close_status TYPE varchar(7);
ALTER TABLE payment_order_guard ADD COLUMN close_claim_token uuid;
ALTER TABLE payment_order_guard ADD COLUMN close_claim_until timestamptz;
ALTER TABLE payment_order_guard ADD CONSTRAINT checkout_closure_state_check CHECK (
    (close_operation_id IS NULL AND close_status IS NULL AND close_reason IS NULL
        AND close_safe_proof IS NULL AND close_safe_at IS NULL AND close_claim_token IS NULL AND close_claim_until IS NULL)
    OR (close_operation_id IS NOT NULL AND close_status IS NOT NULL AND close_status IN ('SAFE', 'HOLD', 'CLOSING')
        AND ((close_safe_proof IS NULL AND close_safe_at IS NULL) OR (close_safe_proof IS NOT NULL AND close_safe_at IS NOT NULL))
        AND (close_status <> 'SAFE' OR close_safe_proof IS NOT NULL)
        AND ((close_status = 'CLOSING' AND close_claim_token IS NOT NULL AND close_claim_until IS NOT NULL)
            OR (close_status <> 'CLOSING' AND close_claim_token IS NULL AND close_claim_until IS NULL)))
);
