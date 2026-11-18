\set ON_ERROR_STOP on
-- Run as the migration owner against the intended LOCAL database only.
-- Precreate distinct individual LOGIN roles and NOLOGIN group roles; supply identifiers via psql -v.
-- Do not grant the operator group to the application/resolver login.
GRANT USAGE ON SCHEMA public TO :"operator_group", :"resolver_group";
GRANT EXECUTE ON FUNCTION public.compensation_submit(uuid,bigint,bigint,text) TO :"operator_group";
GRANT EXECUTE ON FUNCTION public.compensation_claim(bigint) TO :"resolver_group";
GRANT EXECUTE ON FUNCTION public.compensation_hold(uuid,uuid,text) TO :"resolver_group";
GRANT EXECUTE ON FUNCTION public.compensation_retry(uuid,uuid,bigint) TO :"resolver_group";
GRANT EXECUTE ON FUNCTION public.compensation_finalize(uuid,uuid,jsonb,jsonb) TO :"resolver_group";
GRANT EXECUTE ON FUNCTION public.compensation_recurrence(uuid,uuid,text,bigint) TO :"resolver_group";
GRANT SELECT ON public.reconciliation_revision TO :"resolver_group";
-- Assign membership outside this script after validating individual login identity.
