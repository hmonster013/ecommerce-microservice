ALTER TABLE orders ADD COLUMN allocation_phase VARCHAR(32);

ALTER TABLE checkout_attempts ADD COLUMN request_snapshot TEXT;

CREATE TABLE inventory_allocation_intents (
    operation_id UUID PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    target_kind VARCHAR(16) NOT NULL CHECK (target_kind IN ('PRODUCT', 'VARIANT')),
    target_id BIGINT NOT NULL CHECK (target_id > 0),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    owner_id VARCHAR(36) NOT NULL,
    state VARCHAR(32) NOT NULL
);
CREATE INDEX idx_allocation_intents_order ON inventory_allocation_intents(order_id);
