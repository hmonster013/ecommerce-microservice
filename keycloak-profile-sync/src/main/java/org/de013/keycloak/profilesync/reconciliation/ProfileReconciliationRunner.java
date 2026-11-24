package org.de013.keycloak.profilesync.reconciliation;

import org.de013.keycloak.profilesync.ProfileSyncConfiguration;
import org.de013.keycloak.profilesync.UserProfileProvisioningClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class ProfileReconciliationRunner {
    private static final Logger log = LoggerFactory.getLogger(ProfileReconciliationRunner.class);

    public static void main(String[] args) {
        boolean dryRun = false;
        int pageSize = 50;

        for (String arg : args) {
            if ("--dry-run".equalsIgnoreCase(arg)) {
                dryRun = true;
            } else if (arg.startsWith("--page-size=")) {
                try {
                    pageSize = Integer.parseInt(arg.substring("--page-size=".length()));
                } catch (NumberFormatException ignored) {}
            }
        }

        String serverUrl = getEnvOrDefault("KEYCLOAK_SERVER_URL", "http://localhost:8090");
        String realm = getEnvOrDefault("KEYCLOAK_REALM", "ecommerce-realm");
        String clientId = getEnvOrDefault("KEYCLOAK_CLIENT_ID", "user-profile-manager");
        String clientSecret = requiredEnv("KEYCLOAK_CLIENT_SECRET");

        String userServiceUrl = getEnvOrDefault("USER_SERVICE_PROVISION_URL", "http://localhost:8081/internal/keycloak/users");
        String provisionSecret = requiredEnv("USER_PROFILE_PROVISIONER_SECRET");

        log.info("Starting reconciliation for realm={}, dryRun={}", realm, dryRun);

        KeycloakAdminClient adminClient = new KeycloakAdminClient(serverUrl, realm, clientId, clientSecret);
        ProfileSyncConfiguration config = new ProfileSyncConfiguration(
                userServiceUrl, provisionSecret, realm, Duration.ofSeconds(2), Duration.ofSeconds(5)
        );
        UserProfileProvisioningClient provisioningClient = new UserProfileProvisioningClient(config);

        ProfileReconciler reconciler = new ProfileReconciler(adminClient, provisioningClient);
        ReconciliationReport report = reconciler.reconcile(dryRun, pageSize);

        log.info("Reconciliation: total={}, created={}, updated={}, unchanged={}, conflicts={}, skipped={}, failed={}, dryRun={}",
                report.totalKeycloakUsers(), report.createdCount(), report.updatedCount(), report.unchangedCount(),
                report.conflictCount(), report.skippedNoEmailCount(), report.failureCount(), report.dryRun());

        if (report.hasFailures()) {
            System.exit(1);
        }
    }

    private static String getEnvOrDefault(String name, String defaultValue) {
        String val = System.getenv(name);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing environment variable: " + name);
        return value;
    }
}
