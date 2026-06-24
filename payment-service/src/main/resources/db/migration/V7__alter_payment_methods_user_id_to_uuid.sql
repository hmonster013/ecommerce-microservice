-- Alter payment_methods user_id to UUID
ALTER TABLE payment_methods ALTER COLUMN user_id TYPE VARCHAR(36) USING user_id::varchar;
