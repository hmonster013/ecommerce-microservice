package org.de013.userservice.dto;

import lombok.Value;

@Value
public class ProvisionResult {
    UserResponse user;
    boolean created;

    public static ProvisionResult created(UserResponse user) {
        return new ProvisionResult(user, true);
    }

    public static ProvisionResult alreadyExists(UserResponse user) {
        return new ProvisionResult(user, false);
    }
}
