-- Run against public only. Flyway history is checked separately by Flyway.
-- Deliberately includes unexpected relations and constraints: drift must fail closed.
WITH relations AS (
    SELECT c.oid, c.relname, c.relkind, c.relpersistence, c.relrowsecurity, c.relforcerowsecurity,
           c.relispartition, c.reloptions
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'v', 'm', 'f', 'S')
      AND c.relname <> 'flyway_schema_history'
), items AS (
    SELECT 'schema-object' AS kind, t.typname AS name,
           jsonb_build_array(t.typtype, t.typcategory) AS detail
    FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace
    WHERE n.nspname = 'public' AND t.typrelid = 0 AND t.typelem = 0
    UNION ALL
    SELECT 'relation' AS kind, r.relname AS name,
           jsonb_build_array(r.relkind, r.relpersistence, r.relrowsecurity, r.relforcerowsecurity,
                             r.relispartition, r.reloptions) AS detail
    FROM relations r
    UNION ALL
    SELECT 'column', r.relname || '.' || a.attname,
           jsonb_build_array(a.attnum, format_type(a.atttypid, a.atttypmod), a.attnotnull,
                             a.attidentity, a.attgenerated, pg_get_expr(d.adbin, d.adrelid),
                             a.attcollation::regcollation::text)
    FROM relations r JOIN pg_attribute a ON a.attrelid = r.oid AND a.attnum > 0 AND NOT a.attisdropped
    LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
    WHERE r.relkind <> 'S'
    UNION ALL
    SELECT 'constraint', r.relname || '.' || c.conname,
           jsonb_build_array(c.contype, pg_get_constraintdef(c.oid, false), c.convalidated,
                             c.condeferrable, c.condeferred)
    FROM relations r JOIN pg_constraint c ON c.conrelid = r.oid
    UNION ALL
    SELECT 'index', r.relname || '.' || i.relname,
           jsonb_build_array(pg_get_indexdef(i.oid), x.indisvalid, x.indisready,
                             x.indislive, x.indisunique, x.indisprimary,
                             pg_get_expr(x.indpred, x.indrelid))
    FROM relations r JOIN pg_index x ON x.indrelid = r.oid
    JOIN pg_class i ON i.oid = x.indexrelid
    UNION ALL
    SELECT 'sequence', r.relname,
           jsonb_build_array(format_type(s.seqtypid, NULL), s.seqstart, s.seqincrement,
                             s.seqmax, s.seqmin, s.seqcache, s.seqcycle,
                             owner.relname, a.attname, dep.deptype)
    FROM relations r JOIN pg_sequence s ON s.seqrelid = r.oid
    LEFT JOIN pg_depend dep ON dep.classid = 'pg_class'::regclass AND dep.objid = r.oid
        AND dep.refclassid = 'pg_class'::regclass AND dep.refobjsubid > 0 AND dep.deptype IN ('a', 'i')
    LEFT JOIN pg_class owner ON owner.oid = dep.refobjid
    LEFT JOIN pg_attribute a ON a.attrelid = dep.refobjid AND a.attnum = dep.refobjsubid
)
SELECT CASE WHEN EXISTS (
    SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace WHERE n.nspname = 'public'
) OR EXISTS (
    SELECT 1 FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND NOT t.tgisinternal
) THEN ('unsupported public routines or triggers: ' || count(*))::integer::text
ELSE COALESCE(jsonb_agg(jsonb_build_array(kind, name, detail) ORDER BY kind, name, detail::text), '[]'::jsonb)::text END
FROM items;
