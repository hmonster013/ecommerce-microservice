-- Keycloak uses email-as-username; keep both identity columns aligned with the entity.
ALTER TABLE users ALTER COLUMN username TYPE VARCHAR(255);
ALTER TABLE users ALTER COLUMN email TYPE VARCHAR(255);
