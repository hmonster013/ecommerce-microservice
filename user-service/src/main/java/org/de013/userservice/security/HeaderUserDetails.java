package org.de013.userservice.security;

public record HeaderUserDetails(String keycloakId, String username, String email) {
}
