\set ON_ERROR_STOP on
-- Run with search_path set to the cart schema. This script never writes to it.
DO $$
DECLARE has_coupon boolean;
BEGIN
  IF EXISTS (SELECT 1 FROM pg_catalog.pg_attribute a
             JOIN pg_catalog.pg_class c ON c.oid = a.attrelid
             JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = current_schema() AND c.relname = 'carts'
               AND a.attname = 'coupon_code' AND a.attnum > 0 AND NOT a.attisdropped) THEN
    EXECUTE 'SELECT EXISTS (SELECT 1 FROM carts WHERE coupon_code IS NOT NULL)' INTO has_coupon;
    IF has_coupon THEN
      RAISE EXCEPTION 'Cart preflight refused: non-null coupon_code exists';
    END IF;
  END IF;
END $$;

WITH objects AS (
 SELECT c.oid, c.relname, c.relkind
 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname=current_schema() AND c.relname <> 'flyway_schema_history'
   AND c.relkind IN ('r','p','v','m','f','S')
), signature AS (
 SELECT 'object' AS kind, o.relname AS name, o.relkind::text AS definition FROM objects o
 UNION ALL
 SELECT 'column', o.relname || '.' || a.attname,
   concat_ws('|', pg_catalog.format_type(a.atttypid,a.atttypmod), a.attnotnull,
     COALESCE(replace(pg_catalog.pg_get_expr(d.adbin,d.adrelid), quote_ident(current_schema()) || '.', ''), ''),
     a.attidentity, a.attgenerated, a.attcollation::regcollation::text)
 FROM objects o JOIN pg_catalog.pg_attribute a ON a.attrelid=o.oid AND a.attnum>0 AND NOT a.attisdropped
 LEFT JOIN pg_catalog.pg_attrdef d ON d.adrelid=o.oid AND d.adnum=a.attnum
 WHERE o.relkind IN ('r','p','v','m','f')
 UNION ALL
 SELECT 'constraint', o.relname || '.' || c.conname,
    concat_ws('|', replace(pg_catalog.pg_get_constraintdef(c.oid, true), quote_ident(current_schema()) || '.', ''), c.convalidated)
 FROM objects o JOIN pg_catalog.pg_constraint c ON c.conrelid=o.oid
 UNION ALL
 SELECT 'index', o.relname || '.' || i.relname,
    concat_ws('|', replace(pg_catalog.pg_get_indexdef(i.oid), quote_ident(current_schema()) || '.', ''), x.indisvalid, x.indisready, x.indislive)
 FROM objects o JOIN pg_catalog.pg_index x ON x.indrelid=o.oid
 JOIN pg_catalog.pg_class i ON i.oid=x.indexrelid
 UNION ALL
 SELECT 'sequence', o.relname,
   concat_ws('|', s.seqtypid::regtype::text,s.seqstart,s.seqincrement,s.seqmax,s.seqmin,s.seqcache,s.seqcycle,
     COALESCE(t.relname || '.' || a.attname, ''))
 FROM objects o JOIN pg_catalog.pg_sequence s ON s.seqrelid=o.oid
 LEFT JOIN pg_catalog.pg_depend d ON d.classid='pg_catalog.pg_class'::regclass AND d.objid=o.oid
   AND d.refclassid='pg_catalog.pg_class'::regclass AND d.deptype IN ('a','i')
 LEFT JOIN pg_catalog.pg_class t ON t.oid=d.refobjid
 LEFT JOIN pg_catalog.pg_attribute a ON a.attrelid=d.refobjid AND a.attnum=d.refobjsubid
)
SELECT COALESCE(jsonb_agg(jsonb_build_array(kind,name,definition) ORDER BY kind,name,definition), '[]'::jsonb)::text FROM signature;
