-- Insert sample categories
INSERT INTO categories (name, description, slug, parent_id, level, display_order, is_active) VALUES
-- Root categories
('Electronics', 'Electronic devices and accessories', 'electronics', NULL, 0, 1, true),
('Clothing', 'Fashion and apparel', 'clothing', NULL, 0, 2, true),
('Home & Garden', 'Home improvement and garden supplies', 'home-garden', NULL, 0, 3, true),
('Books', 'Books and educational materials', 'books', NULL, 0, 4, true),
('Sports', 'Sports and outdoor equipment', 'sports', NULL, 0, 5, true);

-- Electronics subcategories
INSERT INTO categories (name, description, slug, parent_id, level, display_order, is_active) VALUES
('Smartphones', 'Mobile phones and accessories', 'smartphones', 1, 1, 1, true),
('Laptops', 'Laptop computers and accessories', 'laptops', 1, 1, 2, true),
('Headphones', 'Audio equipment and headphones', 'headphones', 1, 1, 3, true),
('Gaming', 'Gaming consoles and accessories', 'gaming', 1, 1, 4, true);

-- Clothing subcategories
INSERT INTO categories (name, description, slug, parent_id, level, display_order, is_active) VALUES
('Men''s Clothing', 'Clothing for men', 'mens-clothing', 2, 1, 1, true),
('Women''s Clothing', 'Clothing for women', 'womens-clothing', 2, 1, 2, true),
('Shoes', 'Footwear for all', 'shoes', 2, 1, 3, true);

-- Insert sample products
INSERT INTO products (name, description, short_description, sku, price, compare_price, brand, status, is_featured, meta_title, search_keywords) VALUES
('iPhone 15 Pro', 'Latest iPhone with advanced camera system and A17 Pro chip', 'Premium smartphone with professional camera capabilities', 'IPHONE-15-PRO-128', 999.00, 1099.00, 'Apple', 'ACTIVE', true, 'iPhone 15 Pro - Premium Smartphone', 'iphone, apple, smartphone, mobile, camera, a17'),
('MacBook Air M2', 'Lightweight laptop with M2 chip and all-day battery life', 'Ultra-thin laptop perfect for work and creativity', 'MACBOOK-AIR-M2-256', 1199.00, NULL, 'Apple', 'ACTIVE', true, 'MacBook Air M2 - Lightweight Laptop', 'macbook, apple, laptop, m2, ultrabook'),
('Sony WH-1000XM5', 'Industry-leading noise canceling headphones', 'Premium wireless headphones with exceptional sound quality', 'SONY-WH1000XM5-BLACK', 399.99, 449.99, 'Sony', 'ACTIVE', false, 'Sony WH-1000XM5 - Noise Canceling Headphones', 'sony, headphones, wireless, noise canceling, audio'),
('Nike Air Max 270', 'Comfortable running shoes with Max Air cushioning', 'Stylish and comfortable sneakers for everyday wear', 'NIKE-AIRMAX270-WHITE-10', 150.00, NULL, 'Nike', 'ACTIVE', false, 'Nike Air Max 270 - Running Shoes', 'nike, shoes, running, sneakers, air max'),
('Samsung Galaxy S24', 'Latest Samsung flagship with AI features', 'Advanced Android smartphone with Galaxy AI', 'SAMSUNG-S24-256-BLACK', 899.00, 999.00, 'Samsung', 'ACTIVE', true, 'Samsung Galaxy S24 - AI Smartphone', 'samsung, galaxy, android, smartphone, ai');

-- Insert product-category relationships
INSERT INTO product_categories (product_id, category_id, is_primary) VALUES
-- iPhone 15 Pro
(1, 6, true),   -- Primary: Smartphones
(1, 1, false),  -- Secondary: Electronics

-- MacBook Air M2
(2, 7, true),   -- Primary: Laptops
(2, 1, false),  -- Secondary: Electronics

-- Sony Headphones
(3, 8, true),   -- Primary: Headphones
(3, 1, false),  -- Secondary: Electronics

-- Nike Shoes
(4, 12, true),  -- Primary: Shoes
(4, 2, false),  -- Secondary: Clothing

-- Samsung Galaxy S24
(5, 6, true),   -- Primary: Smartphones
(5, 1, false);  -- Secondary: Electronics

-- Insert product variants
INSERT INTO product_variants (product_id, variant_type, name, value, price_adjustment, sku, display_order) VALUES
-- iPhone 15 Pro storage variants
(1, 'CAPACITY', 'Storage', '128GB', 0.00, 'IPHONE-15-PRO-128', 1),
(1, 'CAPACITY', 'Storage', '256GB', 100.00, 'IPHONE-15-PRO-256', 2),
(1, 'CAPACITY', 'Storage', '512GB', 300.00, 'IPHONE-15-PRO-512', 3),

-- iPhone 15 Pro color variants
(1, 'COLOR', 'Color', 'Natural Titanium', 0.00, NULL, 1),
(1, 'COLOR', 'Color', 'Blue Titanium', 0.00, NULL, 2),
(1, 'COLOR', 'Color', 'White Titanium', 0.00, NULL, 3),
(1, 'COLOR', 'Color', 'Black Titanium', 0.00, NULL, 4),

-- MacBook Air M2 variants
(2, 'CAPACITY', 'Storage', '256GB', 0.00, 'MACBOOK-AIR-M2-256', 1),
(2, 'CAPACITY', 'Storage', '512GB', 200.00, 'MACBOOK-AIR-M2-512', 2),
(2, 'COLOR', 'Color', 'Silver', 0.00, NULL, 1),
(2, 'COLOR', 'Color', 'Space Gray', 0.00, NULL, 2),
(2, 'COLOR', 'Color', 'Starlight', 0.00, NULL, 3),
(2, 'COLOR', 'Color', 'Midnight', 0.00, NULL, 4),

-- Nike shoes size variants
(4, 'SIZE', 'Size', 'US 8', 0.00, 'NIKE-AIRMAX270-WHITE-8', 1),
(4, 'SIZE', 'Size', 'US 9', 0.00, 'NIKE-AIRMAX270-WHITE-9', 2),
(4, 'SIZE', 'Size', 'US 10', 0.00, 'NIKE-AIRMAX270-WHITE-10', 3),
(4, 'SIZE', 'Size', 'US 11', 0.00, 'NIKE-AIRMAX270-WHITE-11', 4),
(4, 'SIZE', 'Size', 'US 12', 0.00, 'NIKE-AIRMAX270-WHITE-12', 5);

-- Insert product images
INSERT INTO product_images (product_id, url, alt_text, image_type, display_order, title) VALUES
-- iPhone 15 Pro images
(1, 'https://example.com/images/iphone-15-pro-main.jpg', 'iPhone 15 Pro front view', 'MAIN', 1, 'iPhone 15 Pro'),
(1, 'https://example.com/images/iphone-15-pro-back.jpg', 'iPhone 15 Pro back view', 'GALLERY', 2, 'iPhone 15 Pro Back'),
(1, 'https://example.com/images/iphone-15-pro-side.jpg', 'iPhone 15 Pro side view', 'GALLERY', 3, 'iPhone 15 Pro Side'),

-- MacBook Air M2 images
(2, 'https://example.com/images/macbook-air-m2-main.jpg', 'MacBook Air M2 open view', 'MAIN', 1, 'MacBook Air M2'),
(2, 'https://example.com/images/macbook-air-m2-closed.jpg', 'MacBook Air M2 closed view', 'GALLERY', 2, 'MacBook Air M2 Closed'),

-- Sony headphones images
(3, 'https://example.com/images/sony-wh1000xm5-main.jpg', 'Sony WH-1000XM5 headphones', 'MAIN', 1, 'Sony WH-1000XM5'),

-- Nike shoes images
(4, 'https://example.com/images/nike-airmax270-main.jpg', 'Nike Air Max 270 white', 'MAIN', 1, 'Nike Air Max 270'),

-- Samsung Galaxy S24 images
(5, 'https://example.com/images/samsung-s24-main.jpg', 'Samsung Galaxy S24 front view', 'MAIN', 1, 'Samsung Galaxy S24');

-- Update inventory (will be automatically created by trigger, but let's update with realistic values)
UPDATE inventory SET 
    quantity = 50, 
    min_stock_level = 10, 
    reorder_point = 15,
    reorder_quantity = 25
WHERE product_id = 1;

UPDATE inventory SET 
    quantity = 25, 
    min_stock_level = 5, 
    reorder_point = 8,
    reorder_quantity = 15
WHERE product_id = 2;

UPDATE inventory SET 
    quantity = 100, 
    min_stock_level = 20, 
    reorder_point = 30,
    reorder_quantity = 50
WHERE product_id = 3;

UPDATE inventory SET 
    quantity = 75, 
    min_stock_level = 15, 
    reorder_point = 25,
    reorder_quantity = 40
WHERE product_id = 4;

UPDATE inventory SET 
    quantity = 40, 
    min_stock_level = 8, 
    reorder_point = 12,
    reorder_quantity = 20
WHERE product_id = 5;


