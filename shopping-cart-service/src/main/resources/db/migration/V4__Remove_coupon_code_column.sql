-- Migration V4: Remove coupon_code column from carts table
-- This migration removes the coupon functionality completely

-- Remove the coupon_code column
ALTER TABLE carts DROP COLUMN IF EXISTS coupon_code;

-- Add comment to track the change
COMMENT ON TABLE carts IS 'Shopping cart table - coupon functionality removed in V4';
