package org.de013.keycloak.profilesync;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class ProfileSyncEventListenerProviderFactory implements EventListenerProviderFactory {

    private static final Logger log = Logger.getLogger(ProfileSyncEventListenerProviderFactory.class);
    public static final String PROVIDER_ID = "profile-sync";

    private ProfileSyncConfiguration configuration;
    private UserProfileProvisioningClient provisioningClient;

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new ProfileSyncEventListenerProvider(session, configuration, provisioningClient);
    }

    @Override
    public void init(Config.Scope config) {
        log.info("Initializing ProfileSyncEventListenerProviderFactory");
        this.configuration = new ProfileSyncConfiguration(config);
        this.provisioningClient = new UserProfileProvisioningClient(configuration);
        log.infof("ProfileSync initialized for realm '%s' targeting URL '%s'",
                configuration.getTargetRealm(), configuration.getUserServiceUrl());
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No post-initialization needed
    }

    @Override
    public void close() {
        log.info("Closing ProfileSyncEventListenerProviderFactory");
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
