-- V9__add_gateway_fields_to_payments.sql
-- Add gateway-general fields to payments table for supporting multiple gateways (like VNPay)

ALTER TABLE payments ADD COLUMN gateway_name VARCHAR(20);
ALTER TABLE payments ADD COLUMN gateway_txn_ref VARCHAR(100);
ALTER TABLE payments ADD COLUMN gateway_response TEXT;

CREATE INDEX IF NOT EXISTS idx_gateway_txn_ref ON payments(gateway_txn_ref);
