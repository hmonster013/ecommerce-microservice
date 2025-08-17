-- Update existing users with new secure passwords
UPDATE users SET password = '$2a$12$LQv3c1yqBWVHxkd0LQ1lqu.xQZ4rBx6UVlI8jO/u2OZsCnqDHFLhG' WHERE username = 'admin';
UPDATE users SET password = '$2a$12$8xf4v2HpOy9SRjLp1gtS4OeAGOdhRbbgJVzwGOxZ5uuSiHfDczAJG' WHERE username = 'customer';
