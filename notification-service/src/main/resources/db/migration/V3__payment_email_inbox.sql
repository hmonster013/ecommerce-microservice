CREATE TABLE payment_email_request (
    event_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    canonical_hash CHAR(64) NOT NULL,
    payment_id BIGINT NOT NULL,
    payment_number TEXT NOT NULL,
    order_id BIGINT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    recipient VARCHAR(254) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    notification_id BIGINT NOT NULL UNIQUE REFERENCES notifications(id),
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, channel)
);

CREATE INDEX idx_payment_email_delivery ON payment_email_request (next_attempt_at, event_id) WHERE delivery_status = 'PENDING';

CREATE TABLE payment_email_quarantine (
    id BIGSERIAL PRIMARY KEY,
    source_topic TEXT NOT NULL,
    source_partition INTEGER NOT NULL,
    source_offset BIGINT NOT NULL,
    event_id UUID,
    reason VARCHAR(64) NOT NULL,
    raw_sanitized TEXT NOT NULL,
    raw_sha256 CHAR(64) NOT NULL,
    raw_length BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (source_topic, source_partition, source_offset)
);

CREATE TABLE payment_email_dlt_outbox (
    id UUID PRIMARY KEY,
    quarantine_id BIGINT UNIQUE REFERENCES payment_email_quarantine(id),
    event_id UUID,
    reason VARCHAR(64) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_payment_email_dlt_pending ON payment_email_dlt_outbox (created_at, id) WHERE published_at IS NULL;

CREATE TABLE payment_email_replay_audit (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL,
    operator_name TEXT NOT NULL CHECK (length(trim(operator_name)) > 0),
    reason TEXT NOT NULL CHECK (length(trim(reason)) > 0),
    replayed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
