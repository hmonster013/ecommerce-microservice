package org.de013.apigateway.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

    @NotBlank(message = "Username or email is required")
    String username,

    @NotBlank(message = "Password is required")
    String password
) {
}
