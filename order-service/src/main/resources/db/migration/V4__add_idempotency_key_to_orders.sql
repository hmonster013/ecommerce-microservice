ALTER TABLE orders ADD COLUMN idempotency_key VARCHAR(100);
CREATE UNIQUE INDEX uq_orders_idempotency_key ON orders (idempotency_key);
