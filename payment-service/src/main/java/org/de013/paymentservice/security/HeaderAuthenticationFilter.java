package org.de013.paymentservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Filter that reads user context from headers set by API Gateway
 * and creates Spring Security Authentication object for @PreAuthorize to work
 */
@Component
@Slf4j
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    // Header constants - must match API Gateway
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USERNAME = "X-User-Username";
    private static final String HEADER_USER_EMAIL = "X-User-Email";
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {

        try {
            // Read user context from headers
            String userId = request.getHeader(HEADER_USER_ID);
            String username = request.getHeader(HEADER_USERNAME);
            String email = request.getHeader(HEADER_USER_EMAIL);
            String roles = request.getHeader(HEADER_USER_ROLES);

            // If user context exists, create Authentication object
            if (StringUtils.hasText(userId) && StringUtils.hasText(username)) {
                Authentication auth = createAuthenticationFromHeaders(userId, username, email, roles);
                
                if (auth != null) {
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("Set authentication for user: {} with roles: {}", username, roles);
                }
            } else {
                log.debug("No user context found in headers");
            }

        } catch (Exception e) {
            log.warn("Error processing authentication headers: {}", e.getMessage());
            // Don't fail the request, just continue without authentication
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // Skip filter for public endpoints
        return path.contains("/actuator/") || 
               path.contains("/swagger-ui") || 
               path.contains("/v3/api-docs");
    }

    /**
     * Create Authentication object from headers
     */
    private Authentication createAuthenticationFromHeaders(String userId, String username, String email, String roles) {
        if (userId == null || username == null) {
            return null;
        }

        List<SimpleGrantedAuthority> authorities = parseRoles(roles);
        
        // Create Authentication object with user info
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username, // principal
                null,     // credentials (no password needed)
                authorities // authorities
        );
        
        // Set additional details
        auth.setDetails(new HeaderUserDetails(userId, username, email, roles));
        
        return auth;
    }

    /**
     * Parse roles string into authorities
     */
    private List<SimpleGrantedAuthority> parseRoles(String roles) {
        if (!StringUtils.hasText(roles)) {
            return Collections.emptyList();
        }
        
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(role -> {
                    // Ensure role has ROLE_ prefix for Spring Security
                    if (!role.startsWith("ROLE_")) {
                        return new SimpleGrantedAuthority("ROLE_" + role);
                    }
                    return new SimpleGrantedAuthority(role);
                })
                .collect(Collectors.toList());
    }

    /**
     * Custom UserDetails to hold additional user information from headers
     */
    public static class HeaderUserDetails {
        private final String userId;
        private final String username;
        private final String email;
        private final String roles;

        public HeaderUserDetails(String userId, String username, String email, String roles) {
            this.userId = userId;
            this.username = username;
            this.email = email;
            this.roles = roles;
        }

        public String getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getEmail() { return email; }
        public String getRoles() { return roles; }
    }
}
