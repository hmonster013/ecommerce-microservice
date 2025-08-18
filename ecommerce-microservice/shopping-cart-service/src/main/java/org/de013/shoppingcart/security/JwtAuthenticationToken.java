package org.de013.shoppingcart.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * JWT Authentication Token
 * Custom authentication token that holds JWT-specific information
 */
public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final UserDetails principal;
    private final String token;
    private final String userId;
    private final String sessionId;
    private final boolean isGuest;

    public JwtAuthenticationToken(UserDetails principal, String token, 
                                 Collection<? extends GrantedAuthority> authorities,
                                 String userId, String sessionId, boolean isGuest) {
        super(authorities);
        this.principal = principal;
        this.token = token;
        this.userId = userId;
        this.sessionId = sessionId;
        this.isGuest = isGuest;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    public String getToken() {
        return token;
    }

    public String getUserId() {
        return userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean isGuest() {
        return isGuest;
    }

    public String getUsername() {
        return principal != null ? principal.getUsername() : null;
    }

    @Override
    public String toString() {
        return "JwtAuthenticationToken{" +
                "username='" + getUsername() + '\'' +
                ", userId='" + userId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", isGuest=" + isGuest +
                ", authorities=" + getAuthorities() +
                '}';
    }
}
