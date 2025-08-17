-- Create users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    phone VARCHAR(20),
    address TEXT,
    role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    is_enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better performance
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- Insert default admin user (password: SecureAdmin2025!)
INSERT INTO users (username, email, password, first_name, last_name, role, is_enabled)
VALUES ('admin', 'admin@ecommerce.com', '$2a$12$LQv3c1yqBWVHxkd0LQ1lqu.xQZ4rBx6UVlI8jO/u2OZsCnqDHFLhG', 'Admin', 'User', 'ADMIN', true);

-- Insert sample customer (password: SecureCustomer2025!)
INSERT INTO users (username, email, password, first_name, last_name, role, is_enabled)
VALUES ('customer', 'customer@ecommerce.com', '$2a$12$8xf4v2HpOy9SRjLp1gtS4OeAGOdhRbbgJVzwGOxZ5uuSiHfDczAJG', 'Customer', 'User', 'CUSTOMER', true);
