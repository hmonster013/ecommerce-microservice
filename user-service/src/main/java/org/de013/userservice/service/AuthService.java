package org.de013.userservice.service;

import org.de013.userservice.dto.*;

/**
 * Authentication Service Interface
 * Handles user authentication, registration, and token management
 */
public interface AuthService {

    /**
     * Register a new user
     * @param request User registration details
     * @return Authentication response with JWT token and user info
     */
    AuthResponse register(UserRegistrationDto request);

    /**
     * Authenticate user login
     * @param request Login credentials
     * @return Authentication response with JWT token and user info
     */
    AuthResponse login(UserLoginDto request);

    /**
     * Refresh JWT token
     * @param request Refresh token request
     * @return New authentication response with refreshed token
     */
    LoginResponseDto refreshToken(RefreshTokenDto request);

    /**
     * Logout user (invalidate token)
     * @param token JWT token to invalidate
     */
    void logout(String token);

    /**
     * Validate JWT token
     * @param token JWT token to validate
     * @return true if token is valid, false otherwise
     */
    boolean validateToken(String token);

    /**
     * Get user info from JWT token
     * @param token JWT token
     * @return User response DTO
     */
    UserResponse getUserFromToken(String token);

    /**
     * Change user password
     * @param request Password change request
     * @param currentUsername Current authenticated user's username
     */
    void changePassword(ChangePasswordDto request, String currentUsername);
}
