package org.de013.userservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authentication response containing JWT token and user information")
public class AuthResponse {

    @Schema(description = "JWT access token",
            example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
            required = true)
    private String token;

    @Schema(description = "JWT refresh token",
            example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String refreshToken;

    @Builder.Default
    @Schema(description = "Token type",
            example = "Bearer",
            defaultValue = "Bearer")
    private String type = "Bearer";

    @Schema(description = "User information")
    private UserResponse user;
}
