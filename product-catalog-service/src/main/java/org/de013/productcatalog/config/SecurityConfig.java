package org.de013.productcatalog.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.Assert;
import org.de013.productcatalog.security.CatalogIdentity;
import org.springframework.data.domain.AuditorAware;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private static final String AUDIENCE = "product-catalog-service";

    @Bean
    AuditorAware<String> catalogAuditor() {
        return CatalogIdentity::subject;
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuer) {
        Assert.hasText(issuer, "spring.security.oauth2.resourceserver.jwt.issuer-uri is required");
        return new SupplierJwtDecoder(() -> {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
            OAuth2TokenValidator<Jwt> required = jwt -> jwt.getExpiresAt() != null
                    && jwt.getAudience() != null && jwt.getAudience().contains(AUDIENCE)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Expiration and catalog audience are required", null));
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), required));
            return decoder;
        });
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Object realm = jwt.getClaims().get("realm_access");
            Object roles = realm instanceof Map<?, ?> map ? map.get("roles") : null;
            if (!(roles instanceof Collection<?> values)) return List.of();
            return values.stream().filter(String.class::isInstance).map(String.class::cast)
                    .filter(role -> role.equals("ADMIN") || role.equals("MANAGER"))
                    .<org.springframework.security.core.GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
        });
        http.csrf(AbstractHttpConfigurer::disable).cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/internal/inventory-reservations", "/internal/inventory-reservations/*/release", "/internal/inventory-reservations/*/fulfill")
                        .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(internal(authentication.get())))
                    .requestMatchers(HttpMethod.GET, "/internal/inventory-reservations/*")
                        .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(internal(authentication.get())))
                    .requestMatchers(HttpMethod.GET, "/settings/currency").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/settings/currency").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/exchange-rates", "/products", "/products/*", "/products/sku/*", "/products/*/variants", "/products/*/images", "/products/*/inventory", "/products/*/inventory/check", "/products/*/variants/inventory", "/categories", "/categories/*", "/categories/slug/*", "/categories/*/products", "/categories/*/children", "/categories/*/path", "/search", "/search/suggestions", "/images/*", "/variants/*", "/variants/sku/*", "/variants/*/inventory", "/variants/*/inventory/check", "/variants/*/inventory/available", "/variants/*/inventory/in-stock").permitAll()
                    .requestMatchers(HttpMethod.POST, "/products/*/inventory/add", "/products/*/inventory/remove", "/products/*/inventory/set", "/variants/*/inventory/add", "/variants/*/inventory/remove", "/variants/*/inventory/set").hasAnyRole("ADMIN", "MANAGER")
                    .requestMatchers(HttpMethod.POST, "/products", "/categories", "/products/*/variants", "/products/*/images").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/products/*", "/categories/*", "/categories/*/active", "/categories/*/move", "/variants/*", "/images/*").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/products/*", "/categories/*", "/variants/*", "/images/*").hasRole("ADMIN")
                    .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }

    private static boolean internal(org.springframework.security.core.Authentication authentication) {
        if (!(authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token)) return false;
        Jwt jwt = token.getToken();
        Object resource = jwt.getClaims().get("resource_access");
        Object client = resource instanceof Map<?, ?> map ? map.get(AUDIENCE) : null;
        Object roles = client instanceof Map<?, ?> map ? map.get("roles") : null;
        return "order-service".equals(jwt.getClaimAsString("azp"))
                && roles instanceof Collection<?> values && values.contains("catalog-inventory-reservation");
    }
}
