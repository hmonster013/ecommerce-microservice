package org.de013.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.util.HttpUtils;
import org.de013.userservice.dto.*;
import org.de013.userservice.entity.User;
import org.de013.common.exception.BusinessException;
import org.de013.userservice.security.JwtTokenProvider;
import org.de013.userservice.service.AuthService;
import org.de013.userservice.service.TokenBlacklistService;
import org.de013.userservice.service.UserManagementService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserManagementService userManagementService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public AuthResponse register(UserRegistrationDto request) {
        log.info("Processing registration for user: {}", request.getUsername());
        
        try {
            // Register user through UserManagementService
            UserResponse userResponse = userManagementService.registerUser(request);

            // Load user for JWT generation
            User user = userManagementService.findUserByUsername(request.getUsername());
            
            // Generate tokens
            String accessToken = jwtTokenProvider.generateTokenFromUserDetails(user);
            String refreshToken = jwtTokenProvider.generateRefreshToken(user);

            // Log successful registration
            logAuthenticationEvent("REGISTRATION_SUCCESS", request.getUsername(), null);

            return AuthResponse.builder()
                    .token(accessToken)
                    .refreshToken(refreshToken)
                    .user(userResponse)
                    .build();
                    
        } catch (Exception e) {
            logAuthenticationEvent("REGISTRATION_FAILED", request.getUsername(), e.getMessage());
            throw new BusinessException("Registration failed: " + e.getMessage());
        }
    }

    @Override
    public AuthResponse login(UserLoginDto request) {
        log.info("Processing authentication for user: {}", request.getUsername());
        
        try {
            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );
            
            User user = (User) authentication.getPrincipal();
            
            // Generate tokens
            String accessToken = jwtTokenProvider.generateToken(authentication);
            String refreshToken = jwtTokenProvider.generateRefreshToken(user);

            // Get user response
            UserResponse userResponse = userManagementService.getUserByUsername(user.getUsername());

            // Log successful login
            logAuthenticationEvent("LOGIN_SUCCESS", request.getUsername(), null);

            return AuthResponse.builder()
                    .token(accessToken)
                    .refreshToken(refreshToken)
                    .user(userResponse)
                    .build();
                    
        } catch (AuthenticationException e) {
            logAuthenticationEvent("LOGIN_FAILED", request.getUsername(), e.getMessage());
            throw new BadCredentialsException("Invalid username or password");
        }
    }

    @Override
    public LoginResponseDto refreshToken(RefreshTokenDto request) {
        log.info("Processing token refresh");
        
        try {
            String refreshToken = request.getRefreshToken();
            
            // Validate refresh token
            if (!jwtTokenProvider.validateToken(refreshToken)) {
                throw new BusinessException("Invalid refresh token");
            }
            
            // Extract username from refresh token
            String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
            
            // Load user
            User user = userManagementService.findUserByUsername(username);

            // Generate new tokens
            String newAccessToken = jwtTokenProvider.generateTokenFromUserDetails(user);
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(user);

            // Get user response
            UserResponse userResponse = userManagementService.getUserByUsername(username);
            
            log.info("Token refreshed successfully for user: {}", username);
            
            return LoginResponseDto.builder()
                    .accessToken(newAccessToken)
                    .tokenType("Bearer")
                    .expiresIn(jwtTokenProvider.getTokenRemainingTime(newAccessToken))
                    .refreshToken(newRefreshToken)
                    .user(userResponse)
                    .build();
                    
        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getMessage());
            throw new BusinessException("Token refresh failed: " + e.getMessage());
        }
    }

    @Override
    public void logout(String token) {
        log.info("Processing logout");
        
        try {
            // Extract username from token for logging
            String username = jwtTokenProvider.getUsernameFromToken(token);
            
            // Blacklist the token to invalidate it
            tokenBlacklistService.blacklistToken(token, jwtTokenProvider.getExpirationDateFromToken(token).toInstant());
            
            logAuthenticationEvent("LOGOUT_SUCCESS", username, null);
            log.info("User logged out successfully: {}", username);
            
        } catch (Exception e) {
            log.error("Logout failed: {}", e.getMessage());
            throw new BusinessException("Logout failed: " + e.getMessage());
        }
    }

    @Override
    public boolean validateToken(String token) {
        try {
            return jwtTokenProvider.validateToken(token);
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public UserResponse getUserFromToken(String token) {
        try {
            String username = jwtTokenProvider.getUsernameFromToken(token);
            return userManagementService.getUserByUsername(username);
        } catch (Exception e) {
            log.error("Failed to get user from token: {}", e.getMessage());
            throw new BusinessException("Invalid token");
        }
    }

    @Override
    public void changePassword(ChangePasswordDto request, String currentUsername) {
        log.info("Processing password change for user: {}", currentUsername);
        
        try {
            // Load current user
            User user = userManagementService.findUserByUsername(currentUsername);

            // Verify current password
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new BusinessException("Current password is incorrect");
            }

            // Update password through UserManagementService
            userManagementService.updatePassword(currentUsername, request.getNewPassword());
            
            logAuthenticationEvent("PASSWORD_CHANGE_SUCCESS", currentUsername, null);
            log.info("Password changed successfully for user: {}", currentUsername);
            
        } catch (Exception e) {
            logAuthenticationEvent("PASSWORD_CHANGE_FAILED", currentUsername, e.getMessage());
            throw new BusinessException("Password change failed: " + e.getMessage());
        }
    }

    /**
     * Log authentication events with client information
     */
    private void logAuthenticationEvent(String event, String username, String error) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String clientIp = HttpUtils.getClientIpAddress(request);
                String userAgent = HttpUtils.getUserAgent(request);
                
                if (error != null) {
                    log.warn("Auth Event: {} - User: {}, IP: {}, UserAgent: {}, Error: {}", 
                            event, username, clientIp, userAgent, error);
                } else {
                    log.info("Auth Event: {} - User: {}, IP: {}, UserAgent: {}", 
                            event, username, clientIp, userAgent);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to log authentication event: {}", e.getMessage());
        }
    }
}
