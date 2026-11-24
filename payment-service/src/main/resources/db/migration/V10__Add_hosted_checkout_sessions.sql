CREATE TABLE hosted_checkouts (
    order_id BIGINT PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    payment_id BIGINT NOT NULL UNIQUE REFERENCES payments(id),
    amount NUMERIC(19,2) NOT NULL,
    success_url VARCHAR(500) NOT NULL,
    cancel_url VARCHAR(500) NOT NULL,
    generation INTEGER NOT NULL DEFAULT 0,
    session_id VARCHAR(255) UNIQUE,
    session_state VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    reserved_at TIMESTAMP WITH TIME ZONE NOT NULL
);
