package org.de013.userservice.security;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Authentication Provider that creates Authentication object from headers set by API Gateway
 */
@Component
public class HeaderAuthenticationProvider implements AuthenticationProvider {

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        // This provider doesn't actually authenticate - just creates Authentication object from headers
        // Real authentication is done by API Gateway
        return authentication;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    /**
     * Create Authentication object from headers
     */
    public static Authentication createFromHeaders(String userId, String username, String email, String roles) {
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

    private static List<SimpleGrantedAuthority> parseRoles(String roles) {
        if (roles == null || roles.trim().isEmpty()) {
            return List.of();
        }
        
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
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
