package org.de013.common.security;

public enum UserRole {
    ADMIN,
    CUSTOMER,
    MANAGER,
    SUPPORT;

    public String roleName() {
        return name();
    }
}
