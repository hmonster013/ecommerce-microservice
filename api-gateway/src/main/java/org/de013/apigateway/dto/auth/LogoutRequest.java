package org.de013.apigateway.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(

    @NotBlank(message = "Refresh token is required")
    String refreshToken
) {
}
