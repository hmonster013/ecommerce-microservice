-- Existing financial states are not proof that a provider can no longer charge.
ALTER TABLE payments ADD COLUMN blocking boolean NOT NULL DEFAULT true;
CREATE UNIQUE INDEX uq_payments_blocking_order ON payments (order_id) WHERE blocking;
