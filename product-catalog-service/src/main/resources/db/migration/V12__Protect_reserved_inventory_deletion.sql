CREATE FUNCTION protect_reserved_inventory_deletion() RETURNS trigger AS $$
DECLARE
    kind text;
    target bigint;
BEGIN
    IF TG_TABLE_NAME = 'inventory' THEN
        kind := 'PRODUCT';
        target := OLD.product_id;
    ELSE
        kind := 'VARIANT';
        target := OLD.variant_id;
    END IF;
    IF EXISTS (SELECT 1 FROM inventory_reservations
               WHERE target_kind = kind AND target_id = target AND state = 'RESERVED') THEN
        RAISE EXCEPTION 'Active reservation prevents inventory deletion'
            USING ERRCODE = '23514', CONSTRAINT = 'active_inventory_reservation';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER protect_product_inventory_deletion BEFORE DELETE ON inventory
    FOR EACH ROW EXECUTE FUNCTION protect_reserved_inventory_deletion();
CREATE TRIGGER protect_variant_inventory_deletion BEFORE DELETE ON variant_inventory
    FOR EACH ROW EXECUTE FUNCTION protect_reserved_inventory_deletion();
