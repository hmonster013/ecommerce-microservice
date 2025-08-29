package org.de013.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret:CHANGE_THIS_IN_PRODUCTION_MUST_BE_AT_LEAST_256_BITS_LONG_FOR_HS256_ALGORITHM}")
    private String jwtSecret;

    @Value("${jwt.issuer:ecommerce-platform}")
    private String jwtIssuer;

    /**
     * Get signing key for JWT
     */
    private Key getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Extract username from JWT token
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extract user ID from JWT token
     */
    public Long extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        Object userIdObj = claims.get("userId");
        if (userIdObj instanceof Number) {
            return ((Number) userIdObj).longValue();
        }
        return null;
    }

    /**
     * Extract email from JWT token
     */
    public String extractEmail(String token) {
        Claims claims = extractAllClaims(token);
        return (String) claims.get("email");
    }

    /**
     * Extract roles from JWT token
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Claims claims = extractAllClaims(token);
        return (List<String>) claims.get("roles");
    }

    /**
     * Extract first name from JWT token
     */
    public String extractFirstName(String token) {
        Claims claims = extractAllClaims(token);
        return (String) claims.get("firstName");
    }

    /**
     * Extract last name from JWT token
     */
    public String extractLastName(String token) {
        Claims claims = extractAllClaims(token);
        return (String) claims.get("lastName");
    }

    /**
     * Extract expiration date from JWT token
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extract specific claim from JWT token
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extract all claims from JWT token
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Check if token is expired
     */
    public Boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (Exception e) {
            log.debug("Error checking token expiration: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Validate JWT token
     */
    public Boolean validateToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            
            // Check expiration
            if (isTokenExpired(token)) {
                log.debug("Token is expired");
                return false;
            }
            
            // Check issuer
            String issuer = claims.getIssuer();
            if (!jwtIssuer.equals(issuer)) {
                log.debug("Invalid issuer: expected {}, got {}", jwtIssuer, issuer);
                return false;
            }
            
            // Check required claims
            String username = claims.getSubject();
            if (username == null || username.trim().isEmpty()) {
                log.debug("Username claim is missing or empty");
                return false;
            }
            
            log.debug("Token validation successful for user: {}", username);
            return true;
            
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract JWT token from Authorization header
     */
    public String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
