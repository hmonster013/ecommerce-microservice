package org.de013.userservice.service;

import org.de013.userservice.dto.KeycloakProfileProvisionRequest;
import org.de013.userservice.dto.ProvisionResult;

public interface UserProfileProvisioningService {

    void verifyProvisionerSecret(String secret);

    ProvisionResult provisionUserProfile(KeycloakProfileProvisionRequest request);
}
