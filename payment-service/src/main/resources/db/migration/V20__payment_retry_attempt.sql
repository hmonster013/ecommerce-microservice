CREATE TABLE payment_retry_attempt (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments(id),
    idempotency_key VARCHAR(128) NOT NULL,
    payment_method_id VARCHAR(255) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    provider_key UUID NOT NULL UNIQUE,
    state VARCHAR(16) NOT NULL DEFAULT 'RESERVED' CHECK (state IN ('RESERVED', 'UNCERTAIN', 'COMPLETED', 'REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (payment_id, idempotency_key)
);
CREATE UNIQUE INDEX payment_retry_one_active ON payment_retry_attempt(payment_id) WHERE state IN ('RESERVED', 'UNCERTAIN');
