package org.de013.userservice.service;

import org.de013.userservice.dto.KeycloakProfile;

public interface KeycloakProfileClient {
    KeycloakProfile read(String subject);
    void updateNames(String subject, String firstName, String lastName);
}
