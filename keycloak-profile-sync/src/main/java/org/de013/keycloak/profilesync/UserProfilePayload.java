package org.de013.keycloak.profilesync;

public record UserProfilePayload(
        String keycloakId,
        String username,
        String email,
        String firstName,
        String lastName
) {}
