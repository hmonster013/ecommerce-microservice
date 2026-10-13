package org.de013.keycloak.profilesync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.AbstractKeycloakTransaction;

public class ProfileSyncEventListenerProvider implements EventListenerProvider {

    private static final Logger log = LoggerFactory.getLogger(ProfileSyncEventListenerProvider.class);

    private final KeycloakSession session;
    private final ProfileSyncConfiguration configuration;
    private final UserProfileProvisioningClient provisioningClient;

    public ProfileSyncEventListenerProvider(KeycloakSession session,
                                            ProfileSyncConfiguration configuration,
                                            UserProfileProvisioningClient provisioningClient) {
        this.session = session;
        this.configuration = configuration;
        this.provisioningClient = provisioningClient;
    }

    @Override
    public void onEvent(Event event) {
        if (event != null && event.getError() == null && isTargetRealm(event.getRealmId())
                && (event.getType() == EventType.UPDATE_PROFILE || event.getType() == EventType.UPDATE_EMAIL)) {
            notifyAfterCommit(event.getUserId());
            return;
        }
        if (event == null || event.getType() != EventType.REGISTER) {
            return;
        }

        if (!isTargetRealm(event.getRealmId())) {
            log.debug("Ignoring REGISTER event from non-target realm: {}", event.getRealmId());
            return;
        }

        String userId = event.getUserId();
        log.info("Processing REGISTER event for user: keycloakId={}, realm={}", userId, event.getRealmId());
        provisionUser(userId);
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
        if (adminEvent == null) {
            return;
        }

        if (adminEvent.getError() == null && adminEvent.getOperationType() == OperationType.UPDATE
                && adminEvent.getResourceType() == ResourceType.USER && isTargetRealm(adminEvent.getRealmId())) {
            String path = adminEvent.getResourcePath();
            if (path != null && path.matches("users/[^/]+")) notifyAfterCommit(path.substring("users/".length()));
            return;
        }

        if (adminEvent.getOperationType() != OperationType.CREATE ||
                adminEvent.getResourceType() != ResourceType.USER) {
            return;
        }

        if (!isTargetRealm(adminEvent.getRealmId())) {
            log.debug("Ignoring Admin CREATE USER event from non-target realm: {}", adminEvent.getRealmId());
            return;
        }

        String resourcePath = adminEvent.getResourcePath();
        if (resourcePath == null || resourcePath.isBlank()) {
            log.warn("Admin CREATE USER event missing resource path");
            return;
        }

        // Resource path typically: "users/{id}"
        String userId = resourcePath.startsWith("users/")
                ? resourcePath.substring("users/".length())
                : resourcePath;

        log.info("Processing Admin CREATE USER event for user: keycloakId={}, realm={}", userId, adminEvent.getRealmId());
        provisionUser(userId);
    }

    private void provisionUser(String userId) {
        if (userId == null || userId.isBlank()) {
            log.warn("Cannot provision user: userId is null or empty");
            return;
        }

        RealmModel realm = session.getContext().getRealm();
        if (realm == null) {
            realm = session.realms().getRealmByName(configuration.getTargetRealm());
        }

        if (realm == null) {
            log.warn("Could not resolve realm: {}", configuration.getTargetRealm());
            return;
        }

        UserModel user = session.users().getUserById(realm, userId);
        if (user == null) {
            log.warn("User not found in Keycloak session by id: {}", userId);
            return;
        }

        String username = user.getUsername();
        String email = user.getEmail();

        if (email == null || email.isBlank()) {
            log.warn("User {} ({}) has no email address. Profile provisioning skipped.", userId, username);
            return;
        }

        UserProfilePayload payload = new UserProfilePayload(
                user.getId(),
                username,
                email,
                user.getFirstName(),
                user.getLastName()
        );

        boolean success = provisioningClient.provisionUserProfile(payload);
        if (!success) {
            log.warn("Failed to provision profile in user-service for user: {} ({}). Reconciliation will backfill.",
                    userId, username);
        }
    }

    private boolean isTargetRealm(String realmIdOrName) {
        if (realmIdOrName == null) {
            return false;
        }
        String target = configuration.getTargetRealm();
        if (target.equalsIgnoreCase(realmIdOrName)) {
            return true;
        }
        RealmModel currentRealm = session.getContext().getRealm();
        return currentRealm != null && (target.equalsIgnoreCase(currentRealm.getName()) || target.equalsIgnoreCase(currentRealm.getId()));
    }

    private void notifyAfterCommit(String subject) {
        if (subject == null || subject.isBlank()) return;
        // Capture immutable identity only. The callback must not access the closed Keycloak session.
        UserProfileProvisioningClient client = provisioningClient;
        session.getTransactionManager().enlistAfterCompletion(new AbstractKeycloakTransaction() {
            @Override
            protected void commitImpl() {
                client.syncUserProfile(subject);
            }
            @Override
            protected void rollbackImpl() {
                // No notification for a rolled-back identity change.
            }
        });
    }

    @Override
    public void close() {
        // No resources to close
    }
}
