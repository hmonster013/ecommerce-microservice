package org.de013.shoppingcart.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.security.JwtTokenValidator;
import org.de013.shoppingcart.security.SessionService;
import org.de013.shoppingcart.security.CartOwnershipService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Guest Controller
 * Handles guest session management and cart operations for anonymous users
 */
@RestController
@RequestMapping("/api/v1/guest")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Guest Operations", description = "APIs for guest session and cart management")
public class GuestController {

    private final JwtTokenValidator jwtTokenValidator;
    private final CartSecurityService cartSecurityService;
    private final CartOwnershipService cartOwnershipService;

    // ==================== GUEST SESSION MANAGEMENT ====================

    @Operation(summary = "Create guest session", description = "Create a new guest session for anonymous cart access")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Guest session created successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/session")
    public ResponseEntity<Map<String, Object>> createGuestSession() {
        try {
            log.debug("Creating new guest session");
            
            String sessionId = cartSecurityService.createGuestSession();
            if (sessionId == null) {
                return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to create guest session"
                ));
            }
            
            String token = jwtTokenValidator.generateGuestToken(sessionId);
            
            Map<String, Object> response = Map.of(
                "success", true,
                "token", token,
                "sessionId", sessionId,
                "tokenType", "Bearer",
                "expiresAt", jwtTokenValidator.getExpirationDateFromToken(token),
                "isGuest", true,
                "message", "Guest session created successfully",
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error creating guest session: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @Operation(summary = "Refresh guest token", description = "Refresh guest JWT token with extended expiry")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
        @ApiResponse(responseCode = "401", description = "Invalid or expired token"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/token/refresh")
    public ResponseEntity<Map<String, Object>> refreshGuestToken(
            @Parameter(description = "Authorization header with Bearer token")
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = jwtTokenValidator.extractTokenFromHeader(authHeader);
            
            if (token == null || !jwtTokenValidator.validateToken(token)) {
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "error", "Invalid token"
                ));
            }
            
            // Verify it's a guest token
            if (!jwtTokenValidator.isGuestToken(token)) {
                return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "error", "Only guest tokens can be refreshed here"
                ));
            }
            
            String newToken = jwtTokenValidator.refreshGuestToken(token);
            if (newToken == null) {
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "error", "Failed to refresh token"
                ));
            }
            
            Map<String, Object> response = Map.of(
                "success", true,
                "token", newToken,
                "tokenType", "Bearer",
                "expiresAt", jwtTokenValidator.getExpirationDateFromToken(newToken),
                "message", "Guest token refreshed successfully",
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error refreshing guest token: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @Operation(summary = "Validate guest token", description = "Validate guest JWT token and return token info")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token validation completed"),
        @ApiResponse(responseCode = "401", description = "Invalid token"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/token/validate")
    public ResponseEntity<Map<String, Object>> validateGuestToken(
            @Parameter(description = "JWT token to validate")
            @RequestBody Map<String, String> request) {
        try {
            String token = request.get("token");
            
            if (token == null) {
                return ResponseEntity.status(400).body(Map.of(
                    "valid", false,
                    "error", "Token is required"
                ));
            }
            
            boolean isValid = jwtTokenValidator.validateToken(token);
            boolean isGuest = jwtTokenValidator.isGuestToken(token);
            
            if (!isGuest) {
                return ResponseEntity.status(400).body(Map.of(
                    "valid", false,
                    "error", "Not a guest token"
                ));
            }
            
            Map<String, Object> response = Map.of(
                "valid", isValid,
                "isGuest", true,
                "timestamp", LocalDateTime.now()
            );
            
            if (isValid) {
                String sessionId = jwtTokenValidator.getSessionIdFromToken(token);
                boolean sessionValid = cartSecurityService.isValidGuestSession(sessionId);
                
                response = Map.of(
                    "valid", isValid && sessionValid,
                    "sessionId", sessionId,
                    "sessionValid", sessionValid,
                    "isGuest", true,
                    "expiresAt", jwtTokenValidator.getExpirationDateFromToken(token),
                    "timestamp", LocalDateTime.now()
                );
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error validating guest token: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "valid", false,
                "error", e.getMessage()
            ));
        }
    }

    // ==================== CART TRANSFER ====================

    @Operation(summary = "Transfer guest cart to user", description = "Transfer guest cart ownership to authenticated user after login")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cart transferred successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/cart/{cartId}/transfer")
    public ResponseEntity<Map<String, Object>> transferGuestCartToUser(
            @Parameter(description = "Cart ID", required = true)
            @PathVariable Long cartId,
            @Parameter(description = "Transfer request with userId")
            @RequestBody Map<String, String> request) {
        try {
            String userId = request.get("userId");
            String newSessionId = request.get("sessionId");
            
            if (userId == null) {
                return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "error", "userId is required"
                ));
            }
            
            log.info("Transferring guest cart {} to user {}", cartId, userId);
            
            Map<String, Object> result = cartOwnershipService.transferGuestCartToUser(cartId, userId, newSessionId);
            
            if (Boolean.FALSE.equals(result.get("success"))) {
                String errorCode = (String) result.get("errorCode");
                int statusCode = switch (errorCode) {
                    case "CART_NOT_FOUND", "USER_NOT_FOUND" -> 404;
                    case "INVALID_CART_TYPE" -> 400;
                    default -> 500;
                };
                return ResponseEntity.status(statusCode).body(result);
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error transferring guest cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    // ==================== GUEST SESSION INFO ====================

    @Operation(summary = "Get guest session info", description = "Get information about current guest session")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Session info retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Invalid session"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/session/info")
    public ResponseEntity<Map<String, Object>> getGuestSessionInfo(
            @Parameter(description = "Authorization header with Bearer token")
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = jwtTokenValidator.extractTokenFromHeader(authHeader);
            
            if (token == null || !jwtTokenValidator.validateToken(token) || !jwtTokenValidator.isGuestToken(token)) {
                return ResponseEntity.status(401).body(Map.of(
                    "error", "Invalid guest token"
                ));
            }
            
            String sessionId = jwtTokenValidator.getSessionIdFromToken(token);
            Map<String, Object> sessionInfo = cartSecurityService.getGuestSessionInfo(sessionId);
            
            if (sessionInfo == null) {
                return ResponseEntity.status(404).body(Map.of(
                    "error", "Session not found"
                ));
            }
            
            // Add token info
            sessionInfo.put("tokenValid", true);
            sessionInfo.put("tokenExpiresAt", jwtTokenValidator.getExpirationDateFromToken(token));
            sessionInfo.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(sessionInfo);
            
        } catch (Exception e) {
            log.error("Error getting guest session info: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage()
            ));
        }
    }
}
