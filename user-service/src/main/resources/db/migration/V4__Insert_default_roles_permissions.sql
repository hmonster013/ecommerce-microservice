-- Insert default permissions
INSERT INTO permissions (name, description, resource, action) VALUES
-- User permissions
('USER_CREATE', 'Create new users', 'USER', 'CREATE'),
('USER_READ', 'View user information', 'USER', 'READ'),
('USER_UPDATE', 'Update user information', 'USER', 'UPDATE'),
('USER_DELETE', 'Delete users', 'USER', 'DELETE'),
('USER_LIST', 'List all users', 'USER', 'LIST'),

-- Profile permissions
('PROFILE_READ', 'View own profile', 'PROFILE', 'READ'),
('PROFILE_UPDATE', 'Update own profile', 'PROFILE', 'UPDATE'),

-- Product permissions
('PRODUCT_CREATE', 'Create new products', 'PRODUCT', 'CREATE'),
('PRODUCT_READ', 'View product information', 'PRODUCT', 'READ'),
('PRODUCT_UPDATE', 'Update product information', 'PRODUCT', 'UPDATE'),
('PRODUCT_DELETE', 'Delete products', 'PRODUCT', 'DELETE'),
('PRODUCT_LIST', 'List all products', 'PRODUCT', 'LIST'),

-- Order permissions
('ORDER_CREATE', 'Create new orders', 'ORDER', 'CREATE'),
('ORDER_READ', 'View order information', 'ORDER', 'READ'),
('ORDER_UPDATE', 'Update order status', 'ORDER', 'UPDATE'),
('ORDER_DELETE', 'Cancel orders', 'ORDER', 'DELETE'),
('ORDER_LIST', 'List all orders', 'ORDER', 'LIST'),
('ORDER_READ_OWN', 'View own orders', 'ORDER', 'READ_OWN'),

-- Cart permissions
('CART_CREATE', 'Create shopping cart', 'CART', 'CREATE'),
('CART_READ', 'View shopping cart', 'CART', 'READ'),
('CART_UPDATE', 'Update shopping cart', 'CART', 'UPDATE'),
('CART_DELETE', 'Clear shopping cart', 'CART', 'DELETE'),

-- Payment permissions
('PAYMENT_CREATE', 'Process payments', 'PAYMENT', 'CREATE'),
('PAYMENT_READ', 'View payment information', 'PAYMENT', 'READ'),
('PAYMENT_LIST', 'List all payments', 'PAYMENT', 'LIST'),

-- Admin permissions
('ADMIN_DASHBOARD', 'Access admin dashboard', 'ADMIN', 'READ'),
('ADMIN_REPORTS', 'View system reports', 'ADMIN', 'READ'),
('ADMIN_SETTINGS', 'Manage system settings', 'ADMIN', 'UPDATE');

-- Insert default roles
INSERT INTO roles (name, description) VALUES
('ADMIN', 'System administrator with full access'),
('CUSTOMER', 'Regular customer with limited access'),
('MANAGER', 'Store manager with product and order management access'),
('SUPPORT', 'Customer support with read access to orders and users');

-- Assign permissions to ADMIN role (full access)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ADMIN';

-- Assign permissions to CUSTOMER role (limited access)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'CUSTOMER'
AND p.name IN (
    'PROFILE_READ', 'PROFILE_UPDATE',
    'PRODUCT_READ', 'PRODUCT_LIST',
    'ORDER_CREATE', 'ORDER_READ_OWN',
    'CART_CREATE', 'CART_READ', 'CART_UPDATE', 'CART_DELETE',
    'PAYMENT_CREATE'
);

-- Assign permissions to MANAGER role (product and order management)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'MANAGER'
AND p.name IN (
    'PROFILE_READ', 'PROFILE_UPDATE',
    'PRODUCT_CREATE', 'PRODUCT_READ', 'PRODUCT_UPDATE', 'PRODUCT_DELETE', 'PRODUCT_LIST',
    'ORDER_READ', 'ORDER_UPDATE', 'ORDER_LIST',
    'USER_READ', 'USER_LIST'
);

-- Assign permissions to SUPPORT role (read access)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'SUPPORT'
AND p.name IN (
    'USER_READ', 'USER_LIST',
    'PRODUCT_READ', 'PRODUCT_LIST',
    'ORDER_READ', 'ORDER_LIST',
    'PAYMENT_READ', 'PAYMENT_LIST'
);
