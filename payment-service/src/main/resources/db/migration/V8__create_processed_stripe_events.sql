-- Create table to track processed Stripe events (Idempotency)
CREATE TABLE processed_stripe_events (
    event_id VARCHAR(100) PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
