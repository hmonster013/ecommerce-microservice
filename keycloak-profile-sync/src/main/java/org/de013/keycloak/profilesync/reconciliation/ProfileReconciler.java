package org.de013.keycloak.profilesync.reconciliation;

import org.de013.keycloak.profilesync.UserProfileProvisioningClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class ProfileReconciler {

    private static final Logger log = LoggerFactory.getLogger(ProfileReconciler.class);

    private final KeycloakAdminClient adminClient;
    private final UserProfileProvisioningClient provisioningClient;

    public ProfileReconciler(KeycloakAdminClient adminClient, UserProfileProvisioningClient provisioningClient) {
        this.adminClient = adminClient;
        this.provisioningClient = provisioningClient;
    }

    public ReconciliationReport reconcile(boolean dryRun, int pageSize) {
        if (pageSize <= 0 || pageSize > 1000) throw new IllegalArgumentException("Invalid page size");
        log.info("Starting Keycloak user profile reconciliation (dryRun={}, pageSize={})", dryRun, pageSize);

        String token = adminClient.obtainAccessToken();
        int offset = 0;
        int totalKeycloakUsers = 0;
        int successCount = 0;
        int skippedNoEmailCount = 0;
        int failureCount = 0;
        int created = 0, updated = 0, unchanged = 0, conflicts = 0;

        while (true) {
            List<Map<String, Object>> users = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    users = adminClient.fetchUsers(token, offset, pageSize);
                    break;
                } catch (IllegalStateException ex) {
                    if (attempt == 2) throw ex;
                    token = adminClient.obtainAccessToken();
                }
            }
            if (users == null || users.isEmpty()) {
                break;
            }

            for (Map<String, Object> userData : users) {
                totalKeycloakUsers++;
                String keycloakId = (String) userData.get("id");
                String username = (String) userData.get("username");
                String email = (String) userData.get("email");

                if (email == null || email.isBlank()) {
                    log.warn("Keycloak user {} ({}) has no email address. Skipping profile reconciliation.",
                            keycloakId, username);
                    skippedNoEmailCount++;
                    continue;
                }

                if (dryRun) {
                    log.info("[DRY-RUN] Candidate subject={}; no DB comparison or mutation performed", keycloakId);
                    successCount++;
                } else {
                    String outcome = provisioningClient.syncUserProfile(keycloakId);
                    if ("created".equals(outcome)) { created++; successCount++; }
                    else if ("updated".equals(outcome)) { updated++; successCount++; }
                    else if ("unchanged".equals(outcome)) { unchanged++; successCount++; }
                    else { if ("conflict".equals(outcome)) conflicts++; failureCount++; }
                }
            }

            if (users.size() < pageSize) {
                break;
            }
            offset += pageSize;
        }

        ReconciliationReport report = new ReconciliationReport(
                totalKeycloakUsers, successCount, skippedNoEmailCount, failureCount, dryRun, created, updated, unchanged, conflicts
        );

        log.info("Reconciliation completed: total={}, success={}, skipped={}, failures={}",
                totalKeycloakUsers, successCount, skippedNoEmailCount, failureCount);

        return report;
    }
}
