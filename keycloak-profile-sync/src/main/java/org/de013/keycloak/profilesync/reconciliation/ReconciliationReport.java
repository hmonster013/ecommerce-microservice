package org.de013.keycloak.profilesync.reconciliation;

public record ReconciliationReport(
        int totalKeycloakUsers,
        int successCount,
        int skippedNoEmailCount,
        int failureCount,
        boolean dryRun,
        int createdCount,
        int updatedCount,
        int unchangedCount,
        int conflictCount
) {
    public ReconciliationReport(int total, int success, int skipped, int failure, boolean dryRun) {
        this(total, success, skipped, failure, dryRun, 0, 0, 0, 0);
    }
    public boolean hasFailures() {
        return failureCount > 0;
    }
}
