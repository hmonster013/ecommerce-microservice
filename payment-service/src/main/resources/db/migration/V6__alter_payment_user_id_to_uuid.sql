-- Add V2__alter_payment_user_id_to_uuid.sql
ALTER TABLE payments ALTER COLUMN user_id TYPE VARCHAR(36) USING user_id::varchar;