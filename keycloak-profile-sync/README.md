# Keycloak profile sync and reconciliation

The regular JAR is a Keycloak provider. The `reconciler` Maven profile also copies the standalone CLI's Jackson and SLF4J runtime dependencies, without bundling Keycloak server dependencies into the provider JAR.

From `backend/`:

```powershell
.\mvnw.cmd -pl keycloak-profile-sync -Preconciler clean compile test package
docker build -f keycloak-profile-sync/Dockerfile.reconciler -t de013/ecommerce-keycloak-reconciler:latest .
python keycloak-profile-sync/scripts/verify_reconciler_image.py
```

The image matches the classpath and image name in `kubernetes/jobs/profile-reconciliation-{job,cronjob}.yaml`. It runs as UID/GID 10001. No credentials are baked into the image. The verification script uses Docker Desktop's `host.docker.internal` and disposable HTTP fixtures to test dry run, a fixture sync, enumeration failure, and conflict exit status. It does not contact live Keycloak/user-service or use real credentials.

Configure `KEYCLOAK_SERVER_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET`, `USER_SERVICE_PROVISION_URL`, and `USER_PROFILE_PROVISIONER_SECRET` at runtime. Use the dedicated service account from the profile-sync plan, not an administrative user password. For local containers, use addresses reachable from the container rather than host loopback.

```powershell
docker run --rm --env-file <reconciler-runtime-env-file> de013/ecommerce-keycloak-reconciler:latest --dry-run --page-size=50
```

Dry run enumerates Keycloak users but does not prove that business profiles exist or that writes converge. Check its exit status and summary before scheduling a real run. A real run may update business profiles; use dedicated test accounts for live acceptance. Build/publish the image and provision runtime secrets before deploying the Job/CronJob. Building the image does not establish a five-minute recovery guarantee or successful rollout.

## Controlled development acceptance

The workspace frontend includes `scripts/run-profile-sync-live.mjs` and its
`scripts/profile-sync-live-acceptance.md` runbook. After explicit authorization,
it creates two dedicated fixtures, temporarily uses the built-in Account Console,
and runs this actual CLI image through a fixture allowlist proxy. Real canonical
Keycloak data and the private sync endpoint are used; no unrestricted realm write
is performed. It verifies dry-run0 writes, paged existing-row drift repair and
missing-profile backfill, plus public-profile failure/refresh behavior.

Use the local image tag `de013/ecommerce-keycloak-reconciler:profile-sync-live`
for that harness. It does not modify or deploy the existing CronJob. The proxy
exists only for scoped fault injection and enumeration; it is not production
infrastructure or a new recovery service. A live post-write read failure is not
claimed as a live database-commit failure. Check the scoped report before enabling
the unrestricted production job or asserting a five-minute recovery SLA.

For the separately approved local kind scheduler, use
[`../kubernetes/jobs/profile-reconciliation-kind.md`](../kubernetes/jobs/profile-reconciliation-kind.md).
Its dedicated manifest starts suspended, uses host endpoints and an imported local
image without a registry push. The opt-in `scripts/verify_kind_schedule.py` checks
existing drift before writes and verifies actual scheduled repair of one owned
fixture. It is not a production test and does not change SPI/business source code.
