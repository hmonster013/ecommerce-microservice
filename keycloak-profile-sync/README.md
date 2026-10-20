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
