CREATE TABLE payment_notification_outbox (
    event_id UUID PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments(id),
    notification_type VARCHAR(50) NOT NULL,
    payload TEXT NOT NULL,
    state VARCHAR(20) NOT NULL CHECK (state IN ('PENDING', 'PUBLISHED', 'RECONCILIATION')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (payment_id, notification_type)
);
