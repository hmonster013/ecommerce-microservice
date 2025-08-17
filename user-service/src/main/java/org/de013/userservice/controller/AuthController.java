package org.de013.userservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.de013.common.dto.ApiResponse;
import org.de013.userservice.dto.AuthResponse;
import org.de013.userservice.dto.LoginRequest;
import org.de013.userservice.dto.UserRegistrationRequest;
import org.de013.userservice.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication and registration endpoints")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(
            summary = "Register a new user",
            description = "Create a new user account with the provided information"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "User registered successfully",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid input data or user already exists",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody UserRegistrationRequest request) {
        
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", response));
    }

    @PostMapping("/login")
    @Operation(
            summary = "User login",
            description = "Authenticate user and return JWT token"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Login successful",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Invalid credentials",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        
        AuthResponse response = authService.authenticate(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }
}
