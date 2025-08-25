package org.de013.userservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.de013.common.constant.ApiPaths;
import org.de013.common.controller.BaseController;
import org.de013.common.dto.ApiResponse;
import org.de013.userservice.dto.*;
import org.springframework.security.core.Authentication;
import org.de013.userservice.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.API + ApiPaths.V1 + ApiPaths.AUTH)
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication and registration endpoints")
public class AuthController extends BaseController {

    private final AuthService authService;

    @PostMapping(ApiPaths.REGISTER)
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
            @Valid @RequestBody UserRegistrationDto request) {

        AuthResponse response = authService.register(request);
        return created(response);
    }

    @PostMapping(ApiPaths.LOGIN)
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
            @Valid @RequestBody UserLoginDto request) {

        AuthResponse response = authService.login(request);
        return ok(response);
    }

    @PostMapping(ApiPaths.REFRESH)
    @Operation(
            summary = "Refresh JWT token",
            description = "Refresh expired JWT token using refresh token"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Token refreshed successfully",
                    content = @Content(schema = @Schema(implementation = LoginResponseDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Invalid refresh token",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<LoginResponseDto>> refreshToken(
            @Valid @RequestBody RefreshTokenDto request) {

        LoginResponseDto response = authService.refreshToken(request);
        return ok(response);
    }

    @PostMapping(ApiPaths.LOGOUT)
    @Operation(
            summary = "User logout",
            description = "Logout user and invalidate JWT token"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Logout successful",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<String>> logout(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            authService.logout(token);
        }

        return ok("Logout successful");
    }
}
