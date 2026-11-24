package org.de013.userservice.security;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.List;

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
     * Note: userId here is actually Keycloak UUID (sub claim)
     */
    public static Authentication createFromHeaders(String keycloakId, String username, String email) {
        if (keycloakId == null || username == null) {
            return null;
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username,
                null,
                List.of()
        );

        auth.setDetails(new HeaderUserDetails(keycloakId, username, email));

        return auth;
    }

}
