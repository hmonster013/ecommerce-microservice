package org.de013.apigateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.apigateway.service.KeycloakService;
import org.de013.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth/admin/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin User Management", description = "Admin endpoints for user management backed by Keycloak")
public class AdminUserController {

    private final KeycloakService keycloakService;
    private final WebClient.Builder webClientBuilder;

    @PatchMapping("/{keycloakId}/enable")
    @Operation(summary = "Enable user", description = "Enable a user account in Keycloak")
    public Mono<ResponseEntity<ApiResponse<String>>> enableUser(@PathVariable String keycloakId) {
        log.info("Admin request to enable user: {}", keycloakId);
        return keycloakService.setUserEnabled(keycloakId, true)
                .map(v -> ResponseEntity.ok(ApiResponse.success("User enabled successfully")))
                .defaultIfEmpty(ResponseEntity.ok(ApiResponse.success("User enabled successfully")));
    }

    @PatchMapping("/{keycloakId}/disable")
    @Operation(summary = "Disable user", description = "Disable a user account in Keycloak and logout all sessions")
    public Mono<ResponseEntity<ApiResponse<String>>> disableUser(@PathVariable String keycloakId) {
        log.info("Admin request to disable user: {}", keycloakId);
        return keycloakService.setUserEnabled(keycloakId, false)
                .then(keycloakService.logoutAllSessions(keycloakId))
                .map(v -> ResponseEntity.ok(ApiResponse.success("User disabled successfully")))
                .defaultIfEmpty(ResponseEntity.ok(ApiResponse.success("User disabled successfully")));
    }

    @DeleteMapping("/{keycloakId}")
    @Operation(summary = "Delete user", description = "Delete user from Keycloak and User Service")
    public Mono<ResponseEntity<ApiResponse<String>>> deleteUser(@PathVariable String keycloakId) {
        log.info("Admin request to delete user: {}", keycloakId);
        return keycloakService.deleteUser(keycloakId)
                .then(deleteUserFromUserService(keycloakId))
                .map(v -> ResponseEntity.ok(ApiResponse.success("User deleted successfully")))
                .defaultIfEmpty(ResponseEntity.ok(ApiResponse.success("User deleted successfully")));
    }

    private Mono<Void> deleteUserFromUserService(String keycloakId) {
        return webClientBuilder.build()
                .delete()
                .uri("http://user-service/api/v1/users/internal/by-keycloak-id/" + keycloakId)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("User {} deleted from user-service DB", keycloakId))
                .onErrorResume(e -> {
                    log.error("Failed to delete user {} from user-service DB: {}", keycloakId, e.getMessage());
                    // Log error but don't fail the overall operation if Keycloak deletion succeeded
                    return Mono.empty();
                });
    }
}
