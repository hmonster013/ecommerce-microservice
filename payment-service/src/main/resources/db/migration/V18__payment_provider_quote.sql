ALTER TABLE payments ADD COLUMN provider_exchange_rate BIGINT;
ALTER TABLE payments ADD COLUMN provider_quoted_at TIMESTAMP;
COMMENT ON COLUMN payments.provider_quoted_at IS 'VNPay quote time in Asia/Ho_Chi_Minh (UTC+07:00).';
