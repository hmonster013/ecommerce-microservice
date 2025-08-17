-- Add new audit columns to users table
ALTER TABLE users 
ADD COLUMN account_non_expired BOOLEAN NOT NULL DEFAULT true,
ADD COLUMN account_non_locked BOOLEAN NOT NULL DEFAULT true,
ADD COLUMN credentials_non_expired BOOLEAN NOT NULL DEFAULT true,
ADD COLUMN created_by VARCHAR(100),
ADD COLUMN updated_by VARCHAR(100);

-- Migrate existing users to new role system
-- Assign ADMIN role to existing admin users
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.role = 'ADMIN' AND r.name = 'ADMIN';

-- Assign CUSTOMER role to existing customer users
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.role = 'CUSTOMER' AND r.name = 'CUSTOMER';

-- Assign CUSTOMER role to existing USER role users (if any)
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.role = 'USER' AND r.name = 'CUSTOMER';

-- Drop the old role column (after data migration)
-- Note: Uncomment this after verifying data migration is successful
-- ALTER TABLE users DROP COLUMN role;
