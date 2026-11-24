\set ON_ERROR_STOP on
-- psql -v request_id=UUID -v order_id=123 -v revision=1 -v note='reason' -f operator-resolution.sql
-- Connect with an individual login granted EXECUTE on compensation_submit; never use the runtime login.
SELECT public.compensation_submit(:'request_id'::uuid, :'order_id'::bigint, :'revision'::bigint, :'note');
