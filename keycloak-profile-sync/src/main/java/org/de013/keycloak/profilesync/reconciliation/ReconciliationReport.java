package org.de013.keycloak.profilesync.reconciliation;

public record ReconciliationReport(
        int totalKeycloakUsers,
        int successCount,
        int skippedNoEmailCount,
        int failureCount,
        boolean dryRun
) {
    public boolean hasFailures() {
        return failureCount > 0;
    }
}
