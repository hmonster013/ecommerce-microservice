package org.de013.productcatalog.security;

import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;

public final class CatalogIdentity {
    private CatalogIdentity() { }

    public static Optional<String> subject() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) return Optional.empty();
        return Optional.ofNullable(jwt.getToken().getSubject()).filter(StringUtils::hasText);
    }
}
