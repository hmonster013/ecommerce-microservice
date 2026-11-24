# Profile reconciliation on local kind

This deployment is for the user-approved disposable development cluster only.
Keycloak and user-service remain outside Kubernetes on Docker Desktop/the host.
The existing production-style Job/CronJob manifests are unchanged.

At this docs-only handoff, the local manifest and acceptance scripts remain
uncommitted in the workspace. This documentation commit alone does not ship the
deployment automation; committing those files needs separate approval.

## Target and prerequisites

- kind cluster: `ecommerce-profile-sync`; context: `kind-ecommerce-profile-sync`.
- Namespace: `profile-sync-dev`.
- Isolated kubeconfig and kind binary directory:
  `C:/Users/tranh/AppData/Local/Temp/opencode/kind-profile-reconciler/`.
- Keycloak reachable from Pods at `http://host.docker.internal:8090` and
  user-service at `http://host.docker.internal:8081`.
- Existing confidential client `user-profile-manager` in `ecommerce-realm`.
  Its current realm-management grants are `view-users` and `manage-users`, not
  `realm-admin`. These are broad user-management privileges, not name-only access.
- Secret `profile-reconciliation-credentials` in this namespace contains only
  `KEYCLOAK_PROFILE_CLIENT_SECRET` and `USER_PROFILE_PROVISIONER_SECRET`.
  Provision from authorized environment values through Kubernetes API/stdin.
  Do not put values in YAML, command-line arguments, Git, logs or an env file
  created for this deployment. The source local `.env` remains untracked.

Kubernetes Secrets are readable by a cluster administrator and are not a secret
vault. The isolated kubeconfig is a credential; never commit or share it. This
cluster is not a production deployment or a production security certification.

## Build and load without a registry

From `backend/`:

```powershell
$dir = 'C:\Users\tranh\AppData\Local\Temp\opencode\kind-profile-reconciler'
.\mvnw.cmd -pl keycloak-profile-sync -Preconciler clean compile test package
docker build -f keycloak-profile-sync/Dockerfile.reconciler -t de013/ecommerce-keycloak-reconciler:kind-be1e94f .
python keycloak-profile-sync/scripts/verify_reconciler_image.py --image de013/ecommerce-keycloak-reconciler:kind-be1e94f
python -B -m unittest discover -s keycloak-profile-sync/scripts -p test_verify_kind_schedule.py
& "$dir\kind.exe" load docker-image de013/ecommerce-keycloak-reconciler:kind-be1e94f --name ecommerce-profile-sync
```

The tag identifies the accepted source baseline, not a registry push. Record the
actual image ID for a deployment; do not reuse the tag for changed source without
re-verification and reloading. `imagePullPolicy: Never` intentionally fails if
the image is absent rather than fetching a different registry image.

## Apply safely and verify

```powershell
kubectl --kubeconfig "$dir\kubeconfig" --context kind-ecommerce-profile-sync apply --dry-run=server -f kubernetes/jobs/profile-reconciliation-kind-cronjob.yaml
kubectl --kubeconfig "$dir\kubeconfig" --context kind-ecommerce-profile-sync apply -f kubernetes/jobs/profile-reconciliation-kind-cronjob.yaml
```

The local manifest is **suspended by default**. Applying it again suspends
future schedules; it does not terminate an already running Job. Inspect active
Jobs before cleanup or rollback. It has a five-minute schedule, `Forbid`
concurrency, one retry and a 240-second Job deadline. Startup grace is 120 seconds;
this is not a guaranteed maximum recovery delay. The Pod runs non-root without
a service-account token, privilege escalation or Linux capabilities.

Before resuming, enumerate canonical users and read business profiles without
mutation. Review missing rows, identity differences, collisions and legacy
unlinked rows. An existing CLI `--dry-run` only enumerates Keycloak users; it does
**not** compare DB state. Do not use it alone as drift approval.

For the approved local fixture test, the opt-in script checks that all existing
rows already match canonical state, runs real dry-run and unchanged smoke Jobs,
then creates one new owned fixture. It uses a targeted SQL update only on that
fixture to inject identity drift and set phone/address preservation sentinels.
It resumes the CronJob and watches for an actual controller-created scheduled
Job with the CronJob owner UID and scheduled timestamp annotation. After success
it verifies repair and unchanged existing rows, then deletes its own profile and
Keycloak identity. No global listener or database setting is changed.
Previously existing Job UIDs are excluded from the watch so a retained scheduled
Job cannot be mistaken for evidence of recovery of the newly created fixture.

```powershell
python keycloak-profile-sync/scripts/verify_kind_schedule.py --kubeconfig "$dir\kubeconfig" --artifact "$dir\schedule-acceptance.json" --test-mode --allow-fixture-db-drift --resume-cronjob
```

This command is **not** a routine production health check. It uses local Docker
bootstrap credentials in memory for its fixture setup/cleanup, plus direct local
Postgres access for read-only comparison and owned-row fault injection. Bootstrap
credentials are never given to the reconciler Pod. Do not run concurrently with
other acceptance tests or rerun against an already enabled CronJob without
first suspending and reviewing active Jobs. Failures after resuming suspend
future schedules. Check the artifact and clean up an owned fixture manually if
an active Job prevented safe teardown. Successful completion leaves the approved
dev CronJob enabled.

## Observe and pause

```powershell
kubectl --kubeconfig "$dir\kubeconfig" --context kind-ecommerce-profile-sync -n profile-sync-dev get cronjobs,jobs,pods
kubectl --kubeconfig "$dir\kubeconfig" --context kind-ecommerce-profile-sync -n profile-sync-dev logs job/<job-name>
kubectl --kubeconfig "$dir\kubeconfig" --context kind-ecommerce-profile-sync -n profile-sync-dev patch cronjob keycloak-profile-reconciliation --type=merge -p '{"spec":{"suspend":true}}'
```

Look for `failed=0`, `conflicts=0` and expected created/updated/unchanged counts.
The kube controller retains one successful scheduled Job and three failed Jobs;
acceptance-only manual Jobs have a one-hour TTL (CronJob history cleanup can
remove them sooner because `kubectl --from=cronjob` also sets their owner).
Kubernetes is not an alerting service: inspect failures explicitly. If the host is asleep, Docker is stopped,
or Keycloak/user-service is unavailable, recovery is delayed and Jobs can fail.
Reconciliation repairs identity only, not unsaved phone/address drafts.

## Cleanup

To remove this entire disposable cluster, after requesting cleanup:

```powershell
& "$dir\kind.exe" delete cluster --name ecommerce-profile-sync --kubeconfig "$dir\kubeconfig"
```

This removes its CronJob, Jobs, Pods and dedicated Secret along with the node.
It does not remove application containers or downloaded host Docker images.
No broad Docker prune/reset is needed. Keep the kubeconfig private until cleanup.

## Remaining acceptance boundary

A scheduled repair of an intentionally drifted fixture is not a live database
commit-outage test, and a healthy dev schedule is not production rollout or a
five-minute SLA. The original `ecommerce` Account Console theme is unchanged.
The user accepted the measured dev scope as DONE on2026-10-24 without requiring
additional live DB-commit-outage acceptance or production rollout. Those remain
unperformed, not PASS. See workspace `docs/backend/[DONE] profile-sync-kind-local.md`
and `docs/backend/[DONE] keycloak-profile-sync-verification.md` for measured results.
