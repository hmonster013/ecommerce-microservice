CREATE TABLE payment_outbox (
    event_id UUID PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments(id),
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    state VARCHAR(32) NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING', 'PUBLISHED', 'RECONCILIATION')),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (payment_id, event_type)
);
