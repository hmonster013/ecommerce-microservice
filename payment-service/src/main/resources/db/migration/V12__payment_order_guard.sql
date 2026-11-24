CREATE TABLE payment_order_guard (
    order_id bigint PRIMARY KEY,
    reconciliation_hold boolean NOT NULL DEFAULT false
);

ALTER TABLE payments ADD COLUMN verified_release boolean NOT NULL DEFAULT false;
