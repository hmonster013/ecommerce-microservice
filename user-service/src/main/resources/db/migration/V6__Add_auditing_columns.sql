-- Add auditing columns to the users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_by VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);

-- Update existing null values to a sensible default for users
UPDATE users SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
UPDATE users SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL;

-- Enforce NOT NULL constraint for users
ALTER TABLE users ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE users ALTER COLUMN updated_at SET NOT NULL;

-- Add auditing columns to the roles table
ALTER TABLE roles ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE roles ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE roles ADD COLUMN IF NOT EXISTS created_by VARCHAR(255);
ALTER TABLE roles ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);

-- Update existing null values to a sensible default for roles
UPDATE roles SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
UPDATE roles SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL;

-- Enforce NOT NULL constraint for roles
ALTER TABLE roles ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE roles ALTER COLUMN updated_at SET NOT NULL;

-- Add auditing columns to the permissions table
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS created_by VARCHAR(255);
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);

-- Update existing null values to a sensible default for permissions
UPDATE permissions SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
UPDATE permissions SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL;

-- Enforce NOT NULL constraint for permissions
ALTER TABLE permissions ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE permissions ALTER COLUMN updated_at SET NOT NULL;

