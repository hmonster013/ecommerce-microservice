\set ON_ERROR_STOP on
-- psql -v order_id=123 -v revision=1 -v action=INVESTIGATING -v actor='name' -v note='text' -f operator-audit.sql
-- Database privilege provisioning is external: runtime roles must not update/delete history.
BEGIN;
SELECT order_id FROM reconciliation_finding WHERE order_id = :'order_id'::bigint FOR UPDATE;
SELECT EXISTS (SELECT 1 FROM reconciliation_finding WHERE order_id = :'order_id'::bigint
               AND current_revision = :'revision'::bigint AND current_revision > 0) AS revision_matches \gset
\if :revision_matches
INSERT INTO reconciliation_audit(order_id, revision, db_principal, actor, note, action)
VALUES (:'order_id'::bigint, :'revision'::bigint, current_user, :'actor', :'note', :'action');
COMMIT;
\else
ROLLBACK;
\echo 'Finding revision changed or does not exist; no audit recorded.'
\quit 1
\endif
