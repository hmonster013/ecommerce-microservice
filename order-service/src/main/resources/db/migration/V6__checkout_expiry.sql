ALTER TABLE orders ADD COLUMN checkout_deadline TIMESTAMP;
ALTER TABLE orders ADD COLUMN checkout_close_operation UUID;
ALTER TABLE orders ADD COLUMN checkout_close_safe_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN checkout_close_hold_reason TEXT;
ALTER TABLE orders ADD COLUMN checkout_close_claim UUID;
ALTER TABLE orders ADD COLUMN checkout_close_claim_until TIMESTAMP;

CREATE INDEX idx_orders_checkout_deadline ON orders(allocation_phase, checkout_deadline, id);
