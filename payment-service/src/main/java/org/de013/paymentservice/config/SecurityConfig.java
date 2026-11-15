package org.de013.paymentservice.config;

import lombok.RequiredArgsConstructor;
import org.de013.paymentservice.security.HeaderAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Security configuration for Payment Service
 * <p>
 * Architecture: Trust internal network - API Gateway handles authentication
 * - Webhooks are public (payment gateway callbacks)
 * - All other requests trusted from internal network
 * - User context read from headers forwarded by API Gateway
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final HeaderAuthenticationFilter headerAuthenticationFilter;

    @Bean
    JwtDecoder internalJwtDecoder(@Value("${payment.internal.issuer-uri:}") String issuer) {
        if (issuer.isBlank()) throw new IllegalStateException("payment.internal.issuer-uri is required");
        URI uri = URI.create(issuer);
        if (!uri.isAbsolute() || uri.getHost() == null || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())))
            throw new IllegalStateException("payment.internal.issuer-uri must be an absolute HTTP(S) issuer URI");
        return new JwtDecoder() {
            private volatile JwtDecoder delegate;
            @Override public Jwt decode(String token) throws JwtException {
                JwtDecoder decoder = delegate;
                if (decoder == null) synchronized (this) {
                    decoder = delegate;
                    if (decoder == null) {
                        NimbusJwtDecoder nimbus = JwtDecoders.fromIssuerLocation(issuer);
                        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> {
                            if (jwt.getExpiresAt() == null || jwt.getAudience() == null || !jwt.getAudience().contains("payment-service")
                                    || !"order-service".equals(jwt.getClaimAsString("azp")))
                                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Required claims missing", null));
                            return OAuth2TokenValidatorResult.success();
                        };
                        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), requiredClaims));
                        delegate = decoder = nimbus;
                    }
                }
                return decoder.decode(token);
            }
        };
    }

    @Bean
    @Order(1)
    public SecurityFilterChain internalChain(HttpSecurity http, JwtDecoder internalJwtDecoder) throws Exception {
        http.securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable).cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.POST, "/internal/checkout-closures/{orderId}")
                        .access((authentication, context) -> {
                            var auth = authentication.get();
                            if (!(auth instanceof JwtAuthenticationToken jwt)) return new org.springframework.security.authorization.AuthorizationDecision(false);
                            Object resource = jwt.getToken().getClaims().get("resource_access");
                            boolean allowed = resource instanceof Map<?, ?> m && m.get("payment-service") instanceof Map<?, ?> client
                                    && client.get("roles") instanceof List<?> roles && roles.contains("payment-checkout-close");
                            return new org.springframework.security.authorization.AuthorizationDecision(allowed);
                        }).anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(internalJwtDecoder)));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                // Browser CORS is handled by API Gateway, not this internal service
                .cors(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(headerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
