-- V3__create_processed_event.sql
-- Create processed_event table for tracking processed events (idempotence)

CREATE TABLE processed_event (
    event_id     VARCHAR(100) PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
