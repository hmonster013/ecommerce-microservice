ALTER TABLE users
    DROP COLUMN IF EXISTS password,
    DROP COLUMN IF EXISTS account_non_expired,
    DROP COLUMN IF EXISTS account_non_locked,
    DROP COLUMN IF EXISTS credentials_non_expired,
    DROP COLUMN IF EXISTS is_enabled;