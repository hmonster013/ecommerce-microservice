# Offline schema preflight

Use only isolated, offline databases. Construct a disposable reference database from the
unchanged module migrations with Flyway targeting the expected version (1–4). Validate
its Flyway history and checksums against those migration files with native Flyway before
running this comparison. Never use a hand-built or previously baselined reference.

Configure two distinct PostgreSQL service names through the standard libpq service file
and authenticate through standard libpq facilities; do not place credentials in command
arguments. The connections must permit reading `pg_control_system()`; failure stops the
preflight. No elevated production grant is implied. With `psql` on PATH, run:

```powershell
./schema-preflight.ps1 -ExpectedVersion 3 -ActualService legacy -ReferenceService reference
```

The script checks that the cluster identifier and database name differ, reads schema
metadata, and compares the supported public schema with the supplied reference. It
rejects public routines and user triggers and never changes either database or writes Flyway history. It does not
validate reference checksums itself. A mismatch or unsupported public-schema object is
a stop, not a request to repair or normalize data. Independently validate existing
Flyway history and checksums; an old baseline also requires an actual schema match.
Only after those checks may an operator explicitly run native Flyway baseline for the
matching version. This script does not authorize automatic baseline or repair. Keep
writers stopped between inspection and the operator action; rerun if anything changes.
