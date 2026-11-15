ALTER TABLE payment_order_guard
    ADD COLUMN close_operation_id uuid,
    ADD COLUMN close_status varchar(4),
    ADD COLUMN close_reason text,
    ADD COLUMN close_safe_proof text,
    ADD COLUMN close_safe_at timestamptz,
    ADD CONSTRAINT checkout_closure_state_check CHECK (
        (close_operation_id IS NULL AND close_status IS NULL AND close_reason IS NULL
            AND close_safe_proof IS NULL AND close_safe_at IS NULL)
        OR (close_operation_id IS NOT NULL AND close_status IS NOT NULL AND close_status IN ('SAFE', 'HOLD')
            AND ((close_safe_proof IS NULL AND close_safe_at IS NULL)
                OR (close_safe_proof IS NOT NULL AND close_safe_at IS NOT NULL))
            AND (close_status <> 'SAFE' OR close_safe_proof IS NOT NULL))
    );
