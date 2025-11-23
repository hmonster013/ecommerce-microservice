ALTER TABLE users
ADD COLUMN keycloak_id VARCHAR(36) NULL;

CREATE UNIQUE INDEX idx_users_keycloak_id ON users(keycloak_id);

COMMENT ON COLUMN users.keycloak_id IS 'Keycloak user UUID (sub claim from JWT)';
