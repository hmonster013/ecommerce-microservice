-- V2__alter_order_items_product_id_to_varchar.sql
-- Alter product_id in order_items table from BIGINT to VARCHAR(36) to match other services

ALTER TABLE order_items ALTER COLUMN product_id TYPE VARCHAR(36) USING product_id::varchar;
