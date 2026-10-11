package org.de013.keycloak.profilesync;

import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

public class ProfileSyncEventListenerProvider implements EventListenerProvider {

    private static final Logger log = Logger.getLogger(ProfileSyncEventListenerProvider.class);

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
        if (event == null || event.getType() != EventType.REGISTER) {
            return;
        }

        if (!isTargetRealm(event.getRealmId())) {
            log.debugf("Ignoring REGISTER event from non-target realm: %s", event.getRealmId());
            return;
        }

        String userId = event.getUserId();
        log.infof("Processing REGISTER event for user: keycloakId=%s, realm=%s", userId, event.getRealmId());
        provisionUser(userId);
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
        if (adminEvent == null) {
            return;
        }

        if (adminEvent.getOperationType() != OperationType.CREATE ||
                adminEvent.getResourceType() != ResourceType.USER) {
            return;
        }

        if (!isTargetRealm(adminEvent.getRealmId())) {
            log.debugf("Ignoring Admin CREATE USER event from non-target realm: %s", adminEvent.getRealmId());
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

        log.infof("Processing Admin CREATE USER event for user: keycloakId=%s, realm=%s", userId, adminEvent.getRealmId());
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
            log.warnf("Could not resolve realm: %s", configuration.getTargetRealm());
            return;
        }

        UserModel user = session.users().getUserById(realm, userId);
        if (user == null) {
            log.warnf("User not found in Keycloak session by id: %s", userId);
            return;
        }

        String username = user.getUsername();
        String email = user.getEmail();

        if (email == null || email.isBlank()) {
            log.warnf("User %s (%s) has no email address. Profile provisioning skipped.", userId, username);
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
            log.warnf("Failed to provision profile in user-service for user: %s (%s). Reconciliation will backfill.",
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

    @Override
    public void close() {
        // No resources to close
    }
}
