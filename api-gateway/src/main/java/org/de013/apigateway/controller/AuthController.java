package org.de013.apigateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.apigateway.dto.SyncUserRequest;
import org.de013.apigateway.dto.auth.*;
import org.de013.apigateway.exception.KeycloakException;
import org.de013.apigateway.exception.dto.ErrorResponse;
import org.de013.apigateway.service.KeycloakService;
import org.de013.apigateway.service.UserServiceClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Authentication endpoints for login, register, logout, and token refresh")
public class AuthController {

    private final KeycloakService keycloakService;
    private final UserServiceClient userServiceClient;

    /**
     * Register new user
     */
    @Operation(
            summary = "Register new user",
            description = "Create a new user account in Keycloak with default CUSTOMER role"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "User registered successfully",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data or user already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/register")
    public Mono<ResponseEntity<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {

        log.info("Registration request for username: {}", request.username());

        return keycloakService.createUser(
                        request.username(),
                        request.email(),
                        request.password(),
                        request.firstName(),
                        request.lastName()
                )
                .flatMap(userId ->
                        // Assign default CUSTOMER role
                        keycloakService.assignRole(userId, "CUSTOMER")
                                .then(Mono.just(userId))
                )
                .flatMap(userId -> {
                    // Sync user to User Service database
                    SyncUserRequest syncRequest = new SyncUserRequest(
                            userId,
                            request.username(),
                            request.email(),
                            request.firstName(),
                            request.lastName()
                    );

                    return userServiceClient.syncUser(syncRequest)
                            .then(Mono.just(userId));
                })
                .flatMap(userId ->
                        // Auto login: Get token for newly created user
                        keycloakService.getToken(request.username(), request.password())
                )
                .map(authResponse -> {
                    log.info("User registered and logged in successfully: {}", request.username());
                    return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);
                })
                .onErrorResume(KeycloakException.class, e -> {
                    log.error("Registration failed: {}", e.getMessage());
                    return Mono.just(
                            ResponseEntity.badRequest().body(
                                    new AuthResponse(null, null, null, null, null, null, null)
                            )
                    );
                });
    }

    /**
     * Login user
     */
    @Operation(
            summary = "Login",
            description = "Authenticate user and obtain JWT access token and refresh token"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Login successful",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid credentials",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {

        log.info("Login attempt for user: {}", request.username());

        return keycloakService.getToken(request.username(), request.password())
                .map(authResponse -> {
                    log.info("User logged in successfully: {}", request.username());
                    return ResponseEntity.ok(authResponse);
                })
                .onErrorResume(KeycloakException.class, e -> {
                    log.warn("Login failed for user {}: {}", request.username(), e.getMessage());
                    return Mono.just(
                            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                                    new AuthResponse(null, null, null, null, null, null, null)
                            )
                    );
                });
    }

    /**
     * Refresh access token
     */
    @Operation(
            summary = "Refresh token",
            description = "Obtain a new access token using a valid refresh token"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Token refreshed successfully",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid or expired refresh token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/refresh")
    public Mono<ResponseEntity<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {

        log.debug("Token refresh request");

        return keycloakService.refreshToken(request.refreshToken())
                .map(authResponse -> {
                    log.debug("Token refreshed successfully");
                    return ResponseEntity.ok(authResponse);
                })
                .onErrorResume(KeycloakException.class, e -> {
                    log.warn("Token refresh failed: {}", e.getMessage());
                    return Mono.just(
                            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                                    new AuthResponse(null, null, null, null, null, null, null)
                            )
                    );
                });
    }

    /**
     * Logout user
     */
    @Operation(
            summary = "Logout",
            description = "Revoke refresh token and end user session"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logout successful"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(@Valid @RequestBody LogoutRequest request) {

        log.info("Logout request");

        return keycloakService.logout(request.refreshToken())
                .thenReturn(okResponse())
                .onErrorResume(e -> {
                    log.warn("Logout completed with warning: {}", e.getMessage());
                    // Return success even if logout fails (token might be already expired)
                    return Mono.just(okResponse());
                });
    }

    private static ResponseEntity<Void> okResponse() {
        return ResponseEntity.ok().build();
    }
}
