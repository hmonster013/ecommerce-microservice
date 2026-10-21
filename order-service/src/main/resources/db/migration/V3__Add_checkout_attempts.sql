ALTER TABLE orders ADD COLUMN checkout_key VARCHAR(36);
CREATE UNIQUE INDEX uk_orders_checkout_key ON orders(checkout_key);
CREATE TABLE checkout_attempts (
    attempt_key VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
