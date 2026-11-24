-- V1 delegated these indexes to Hibernate, which validate mode does not create.
-- Existing Hibernate-managed databases may already have them.
CREATE INDEX IF NOT EXISTS idx_notification_user_id ON notifications (user_id);
CREATE INDEX IF NOT EXISTS idx_notification_status ON notifications (status);
CREATE INDEX IF NOT EXISTS idx_notification_channel ON notifications (channel);
CREATE INDEX IF NOT EXISTS idx_notification_created_at ON notifications (created_at);

-- IF NOT EXISTS alone also accepts an unrelated or incorrectly defined index.
DO $$
DECLARE
    expected RECORD;
BEGIN
    FOR expected IN SELECT * FROM (VALUES
        ('idx_notification_user_id', 'user_id'),
        ('idx_notification_status', 'status'),
        ('idx_notification_channel', 'channel'),
        ('idx_notification_created_at', 'created_at')
    ) AS definitions(index_name, column_name)
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_index i
            JOIN pg_class idx ON idx.oid = i.indexrelid
            JOIN pg_class tbl ON tbl.oid = i.indrelid
            JOIN pg_namespace ns ON ns.oid = tbl.relnamespace
            JOIN pg_am am ON am.oid = idx.relam
            JOIN pg_attribute a ON a.attrelid = tbl.oid AND a.attnum = i.indkey[0]
            JOIN pg_opclass opc ON opc.oid = i.indclass[0]
            WHERE ns.nspname = current_schema() AND tbl.relname = 'notifications'
              AND idx.relname = expected.index_name AND a.attname = expected.column_name
              AND am.amname = 'btree' AND i.indisvalid AND i.indisready
              AND NOT i.indisunique AND i.indnkeyatts = 1 AND i.indnatts = 1
              AND i.indexprs IS NULL AND i.indpred IS NULL
              AND i.indoption[0] = 0
              AND opc.opcdefault AND opc.opcmethod = idx.relam
              AND i.indcollation[0] = a.attcollation
        ) THEN
            RAISE EXCEPTION 'Unexpected definition for notification index %', expected.index_name;
        END IF;
    END LOOP;
END $$;
