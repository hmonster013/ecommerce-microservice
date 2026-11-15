ALTER TABLE orders ADD COLUMN accepted_payment_id BIGINT;
ALTER TABLE orders ADD COLUMN accepted_payment_number TEXT;

CREATE TABLE payment_event_inbox (
    event_id UUID PRIMARY KEY,
    payload_hash CHAR(64) NOT NULL,
    result VARCHAR(32) NOT NULL
);

CREATE TABLE payment_event_quarantine (
    topic TEXT NOT NULL,
    partition_id INTEGER NOT NULL,
    record_offset BIGINT NOT NULL,
    reason VARCHAR(64) NOT NULL,
    raw_payload TEXT,
    payload_length INTEGER NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    truncated BOOLEAN NOT NULL,
    PRIMARY KEY (topic, partition_id, record_offset)
);
