package org.de013.apigateway.service;

import lombok.extern.slf4j.Slf4j;
import org.de013.apigateway.dto.auth.AuthResponse;
import org.de013.apigateway.exception.KeycloakException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class KeycloakService {

    private final WebClient webClient;
    private final String serverUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    private final String adminUsername;
    private final String adminPassword;

    public KeycloakService(
            @Value("${keycloak.server-url}") String serverUrl,
            @Value("${keycloak.realm}") String realm,
            @Value("${keycloak.client-id}") String clientId,
            @Value("${keycloak.client-secret}") String clientSecret,
            @Value("${keycloak.admin-username}") String adminUsername,
            @Value("${keycloak.admin-password}") String adminPassword) {
        this.serverUrl = serverUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        // Create WebClient without @LoadBalanced to use direct URLs
        this.webClient = WebClient.builder().build();
    }

    /**
     * Create user in Keycloak
     */
    public Mono<String> createUser(String username, String email, String password,
                                   String firstName, String lastName) {

        return getAdminToken()
                .flatMap(adminToken -> {
                    // Create user representation
                    Map<String, Object> user = new HashMap<>();
                    user.put("username", username);
                    user.put("email", email);
                    user.put("firstName", firstName);
                    user.put("lastName", lastName);
                    user.put("enabled", true);
                    user.put("emailVerified", true);

                    // Set password
                    Map<String, Object> credential = new HashMap<>();
                    credential.put("type", "password");
                    credential.put("value", password);
                    credential.put("temporary", false);
                    user.put("credentials", List.of(credential));

                    // Call Keycloak Admin API
                    return webClient
                            .post()
                            .uri(serverUrl + "/admin/realms/" + realm + "/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(user)
                            .exchangeToMono(response -> {
                                if (response.statusCode().equals(HttpStatus.CREATED)) {
                                    // Extract user ID from Location header
                                    String location = response.headers().asHttpHeaders().getLocation().toString();
                                    String userId = location.substring(location.lastIndexOf('/') + 1);
                                    log.info("User created successfully in Keycloak: {}", userId);
                                    return Mono.just(userId);
                                } else {
                                    return response.bodyToMono(String.class)
                                            .flatMap(errorBody -> {
                                                log.error("Failed to create user: {}", errorBody);
                                                return Mono.error(new KeycloakException("User creation failed: " + errorBody));
                                            });
                                }
                            });
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("Keycloak API error: {}", e.getResponseBodyAsString());
                    if (e.getStatusCode().equals(HttpStatus.CONFLICT)) {
                        return Mono.error(new KeycloakException("Username or email already exists"));
                    }
                    return Mono.error(new KeycloakException("Failed to create user: " + e.getMessage()));
                });
    }

    /**
     * Assign role to user
     */
    public Mono<Void> assignRole(String userId, String roleName) {
        return getAdminToken()
                .flatMap(adminToken ->
                        // Get role representation
                        webClient
                                .get()
                                .uri(serverUrl + "/admin/realms/" + realm + "/roles/" + roleName)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .retrieve()
                                .bodyToMono(Map.class)
                                .flatMap(role ->
                                        // Assign role to user
                                        webClient
                                                .post()
                                                .uri(serverUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm")
                                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .bodyValue(List.of(role))
                                                .retrieve()
                                                .bodyToMono(Void.class)
                                                .doOnSuccess(v -> log.info("Role '{}' assigned to user {}", roleName, userId))
                                )
                )
                .onErrorResume(e -> {
                    log.error("Failed to assign role: {}", e.getMessage());
                    return Mono.error(new KeycloakException("Failed to assign role"));
                });
    }

    /**
     * Get token for user (password grant)
     */
    public Mono<AuthResponse> getToken(String username, String password) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("username", username);
        formData.add("password", password);
        formData.add("scope", "openid profile email");

        return webClient
                .post()
                .uri(serverUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(AuthResponse.class)
                .doOnSuccess(token -> log.debug("Token obtained for user: {}", username))
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("Authentication failed for user {}: {}", username, e.getResponseBodyAsString());
                    return Mono.error(new KeycloakException("Invalid credentials"));
                });
    }

    /**
     * Refresh access token
     */
    public Mono<AuthResponse> refreshToken(String refreshToken) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("refresh_token", refreshToken);

        return webClient
                .post()
                .uri(serverUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(AuthResponse.class)
                .doOnSuccess(token -> log.debug("Token refreshed successfully"))
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("Token refresh failed: {}", e.getResponseBodyAsString());
                    return Mono.error(new KeycloakException("Invalid refresh token"));
                });
    }

    /**
     * Logout user (revoke refresh token)
     */
    public Mono<Void> logout(String refreshToken) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("refresh_token", refreshToken);

        return webClient
                .post()
                .uri(serverUrl + "/realms/" + realm + "/protocol/openid-connect/logout")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("User logged out successfully"))
                .onErrorResume(e -> {
                    log.warn("Logout failed: {}", e.getMessage());
                    // Don't fail logout even if token is already invalid
                    return Mono.empty();
                });
    }

    /**
     * Get admin access token for Keycloak Admin API
     */
    private Mono<String> getAdminToken() {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", "admin-cli");
        formData.add("username", adminUsername);
        formData.add("password", adminPassword);

        return webClient
                .post()
                .uri(serverUrl + "/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(AuthResponse.class)
                .map(AuthResponse::getAccessToken)
                .doOnSuccess(token -> log.debug("Admin token obtained"))
                .onErrorResume(e -> {
                    log.error("Failed to get admin token: {}", e.getMessage());
                    return Mono.error(new KeycloakException("Failed to authenticate with Keycloak Admin"));
                });
    }
}
