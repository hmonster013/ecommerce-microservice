package org.de013.apigateway.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthResponse(

    @JsonProperty("access_token")
    String accessToken,

    @JsonProperty("refresh_token")
    String refreshToken,

    @JsonProperty("id_token")
    String idToken,

    @JsonProperty("expires_in")
    Integer expiresIn,

    @JsonProperty("refresh_expires_in")
    Integer refreshExpiresIn,

    @JsonProperty("token_type")
    String tokenType,

    @JsonProperty("scope")
    String scope
) {
}
