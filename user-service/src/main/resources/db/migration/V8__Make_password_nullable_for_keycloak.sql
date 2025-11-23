ALTER TABLE users
ALTER COLUMN password DROP NOT NULL;

COMMENT ON COLUMN users.password IS 'Password hash (NULL for Keycloak-managed users)';

ALTER TABLE users DROP COLUMN IF EXISTS role;
