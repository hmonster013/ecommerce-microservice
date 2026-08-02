package org.de013.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record SyncUserRequest(
        @NotBlank(message = "Keycloak ID is required")
        String keycloakId,

        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        String firstName,

        String lastName
) {}
