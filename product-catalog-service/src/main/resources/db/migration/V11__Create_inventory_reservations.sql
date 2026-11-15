CREATE TABLE inventory_reservations (
    operation_id UUID PRIMARY KEY,
    target_kind VARCHAR(7) NOT NULL CHECK (target_kind IN ('PRODUCT', 'VARIANT')),
    target_id BIGINT NOT NULL CHECK (target_id > 0),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    owner VARCHAR(255) NOT NULL CHECK (length(trim(owner)) > 0),
    state VARCHAR(9) NOT NULL CHECK (state IN ('RESERVED', 'RELEASED', 'FULFILLED', 'REJECTED')),
    reason VARCHAR(18) CHECK (reason IN ('INSUFFICIENT_STOCK', 'TARGET_NOT_FOUND')),
    CHECK ((state = 'REJECTED') = (reason IS NOT NULL))
);
CREATE INDEX idx_reservation_target_state ON inventory_reservations (target_kind, target_id, state);
