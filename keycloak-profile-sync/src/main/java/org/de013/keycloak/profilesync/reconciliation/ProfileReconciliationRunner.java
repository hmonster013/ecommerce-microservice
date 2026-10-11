package org.de013.keycloak.profilesync.reconciliation;

import org.de013.keycloak.profilesync.ProfileSyncConfiguration;
import org.de013.keycloak.profilesync.UserProfileProvisioningClient;

import java.time.Duration;

public class ProfileReconciliationRunner {

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
        String clientId = getEnvOrDefault("KEYCLOAK_CLIENT_ID", "api-gateway");
        String clientSecret = getEnvOrDefault("KEYCLOAK_CLIENT_SECRET", "api-gateway-secret-change-in-production");

        String userServiceUrl = getEnvOrDefault("USER_SERVICE_PROVISION_URL", "http://localhost:8081/internal/keycloak/users");
        String provisionSecret = getEnvOrDefault("USER_PROFILE_PROVISIONER_SECRET", "dev-profile-provisioner-secret");

        System.out.println("Starting profile reconciliation runner...");
        System.out.println("Target realm: " + realm);
        System.out.println("User service URL: " + userServiceUrl);
        System.out.println("Dry-run mode: " + dryRun);

        KeycloakAdminClient adminClient = new KeycloakAdminClient(serverUrl, realm, clientId, clientSecret);
        ProfileSyncConfiguration config = new ProfileSyncConfiguration(
                userServiceUrl, provisionSecret, realm, Duration.ofSeconds(2), Duration.ofSeconds(5)
        );
        UserProfileProvisioningClient provisioningClient = new UserProfileProvisioningClient(config);

        ProfileReconciler reconciler = new ProfileReconciler(adminClient, provisioningClient);
        ReconciliationReport report = reconciler.reconcile(dryRun, pageSize);

        System.out.println("=========================================");
        System.out.println("KEYCLOAK PROFILE RECONCILIATION SUMMARY");
        System.out.println("Total Keycloak Users Examined: " + report.totalKeycloakUsers());
        System.out.println("Successfully Reconciled:       " + report.successCount());
        System.out.println("Skipped (No Email):            " + report.skippedNoEmailCount());
        System.out.println("Failed:                        " + report.failureCount());
        System.out.println("Dry Run:                       " + report.dryRun());
        System.out.println("Status:                        " + (report.hasFailures() ? "FAILED" : "SUCCESS"));
        System.out.println("=========================================");

        if (report.hasFailures()) {
            System.exit(1);
        }
    }

    private static String getEnvOrDefault(String name, String defaultValue) {
        String val = System.getenv(name);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}
