package org.de013.keycloak.profilesync.reconciliation;

import org.de013.keycloak.profilesync.UserProfilePayload;
import org.de013.keycloak.profilesync.UserProfileProvisioningClient;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

public class ProfileReconciler {

    private static final Logger log = Logger.getLogger(ProfileReconciler.class);

    private final KeycloakAdminClient adminClient;
    private final UserProfileProvisioningClient provisioningClient;

    public ProfileReconciler(KeycloakAdminClient adminClient, UserProfileProvisioningClient provisioningClient) {
        this.adminClient = adminClient;
        this.provisioningClient = provisioningClient;
    }

    public ReconciliationReport reconcile(boolean dryRun, int pageSize) {
        log.infof("Starting Keycloak user profile reconciliation (dryRun=%b, pageSize=%d)", dryRun, pageSize);

        String token = adminClient.obtainAccessToken();
        int offset = 0;
        int totalKeycloakUsers = 0;
        int successCount = 0;
        int skippedNoEmailCount = 0;
        int failureCount = 0;

        while (true) {
            List<Map<String, Object>> users = adminClient.fetchUsers(token, offset, pageSize);
            if (users == null || users.isEmpty()) {
                break;
            }

            for (Map<String, Object> userData : users) {
                totalKeycloakUsers++;
                String keycloakId = (String) userData.get("id");
                String username = (String) userData.get("username");
                String email = (String) userData.get("email");
                String firstName = (String) userData.get("firstName");
                String lastName = (String) userData.get("lastName");

                if (email == null || email.isBlank()) {
                    log.warnf("Keycloak user %s (%s) has no email address. Skipping profile reconciliation.",
                            keycloakId, username);
                    skippedNoEmailCount++;
                    continue;
                }

                if (dryRun) {
                    log.infof("[DRY-RUN] Would reconcile user: keycloakId=%s, username=%s", keycloakId, username);
                    successCount++;
                } else {
                    UserProfilePayload payload = new UserProfilePayload(
                            keycloakId, username, email, firstName, lastName
                    );
                    boolean provisioned = provisioningClient.provisionUserProfile(payload);
                    if (provisioned) {
                        successCount++;
                    } else {
                        log.warnf("Failed to reconcile profile for user: keycloakId=%s, username=%s", keycloakId, username);
                        failureCount++;
                    }
                }
            }

            if (users.size() < pageSize) {
                break;
            }
            offset += pageSize;
        }

        ReconciliationReport report = new ReconciliationReport(
                totalKeycloakUsers, successCount, skippedNoEmailCount, failureCount, dryRun
        );

        log.infof("Reconciliation completed: total=%d, success=%d, skipped=%d, failures=%d",
                totalKeycloakUsers, successCount, skippedNoEmailCount, failureCount);

        return report;
    }
}
