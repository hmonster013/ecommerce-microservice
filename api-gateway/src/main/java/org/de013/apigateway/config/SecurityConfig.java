package org.de013.apigateway.config;

import org.de013.common.security.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${app.security.permit-all-swagger-paths:true}")
    private boolean permitAllSwaggerPaths;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchanges -> {
                    if (permitAllSwaggerPaths) {
                        // Allow Swagger UI and API docs access without authentication
                        exchanges.pathMatchers("/actuator/**", "/swagger-ui.html", "/swagger-ui/**", "/v1/api-docs/**", "/webjars/**", "/api/*/v1/api-docs/**", "/api/*/*/v1/api-docs/**").permitAll();
                    } else {
                        // Require authentication for all other paths
                        exchanges.pathMatchers("/actuator/**", "/swagger-ui.html", "/swagger-ui/**", "/v1/api-docs/**", "/webjars/**", "/api/*/v1/api-docs/**", "/api/*/*/v1/api-docs/**").hasRole(UserRole.ADMIN.roleName());
                    }

                    exchanges
                            // ========== CIRCUIT BREAKER FALLBACK ==========
                            // Internal error responder for tripped circuit breakers — no sensitive
                            // data, access control is already enforced on the original route.
                            .pathMatchers("/fallback/**").permitAll()

                            // ========== USER SERVICE ==========
                            // Deny public access to internal endpoints (service-to-service private communication only)
                            .pathMatchers("/api/v1/user-service/users/internal/**", "/api/v1/user-service/internal/**").denyAll()

                            // Profile endpoints (authenticated users only)
                            .pathMatchers(HttpMethod.GET, "/api/v1/user-service/users/profile").authenticated()
                            .pathMatchers(HttpMethod.PUT, "/api/v1/user-service/users/profile").authenticated()

                            // Admin-only user management endpoints
                            .pathMatchers("/api/v1/user-service/users/**").hasRole(UserRole.ADMIN.roleName())

                            // ========== PRODUCT CATALOG SERVICE ==========
                            .pathMatchers("/api/v1/product-catalog-service/internal/**").denyAll()
                            // Currency display rates are public; rate administration is ADMIN-only.
                            .pathMatchers(HttpMethod.GET, "/api/v1/product-catalog-service/exchange-rates").permitAll()
                            .pathMatchers(HttpMethod.GET, "/api/v1/product-catalog-service/settings/currency").hasRole(UserRole.ADMIN.roleName())
                            .pathMatchers(HttpMethod.PUT, "/api/v1/product-catalog-service/settings/currency").hasRole(UserRole.ADMIN.roleName())

                            // Deny browser access to private order-stock operations (reserve/release/fulfill)
                            .pathMatchers(
                                    "/api/v1/product-catalog-service/products/*/inventory/reserve",
                                    "/api/v1/product-catalog-service/products/*/inventory/release",
                                    "/api/v1/product-catalog-service/products/*/inventory/fulfill",
                                    "/api/v1/product-catalog-service/variants/*/inventory/reserve",
                                    "/api/v1/product-catalog-service/variants/*/inventory/release",
                                    "/api/v1/product-catalog-service/variants/*/inventory/fulfill"
                            ).denyAll()

                            // Admin & Manager stock adjustments (add/remove/set) - matched before general wildcards
                            .pathMatchers(
                                    HttpMethod.POST,
                                    "/api/v1/product-catalog-service/products/*/inventory/add",
                                    "/api/v1/product-catalog-service/products/*/inventory/remove",
                                    "/api/v1/product-catalog-service/products/*/inventory/set",
                                    "/api/v1/product-catalog-service/variants/*/inventory/add",
                                    "/api/v1/product-catalog-service/variants/*/inventory/remove",
                                    "/api/v1/product-catalog-service/variants/*/inventory/set"
                            ).hasAnyRole(UserRole.ADMIN.roleName(), UserRole.MANAGER.roleName())

                            // Public read access to products, categories, search, images, variants, and stock checks
                            .pathMatchers(HttpMethod.GET, "/api/v1/product-catalog-service/products/**").permitAll()
                            .pathMatchers(HttpMethod.GET, "/api/v1/product-catalog-service/categories/**").permitAll()
                            .pathMatchers(HttpMethod.GET, "/api/v1/product-catalog-service/search/**").permitAll()
                            .pathMatchers(HttpMethod.GET, "/api/v1/product-catalog-service/images/**").permitAll()
                            .pathMatchers(HttpMethod.GET, "/api/v1/product-catalog-service/variants/**").permitAll()

                            // Admin-only metadata management
                            .pathMatchers(HttpMethod.POST, "/api/v1/product-catalog-service/products/**").hasRole(UserRole.ADMIN.roleName())
                            .pathMatchers(HttpMethod.PUT, "/api/v1/product-catalog-service/products/**").hasRole(UserRole.ADMIN.roleName())
                            .pathMatchers(HttpMethod.PATCH, "/api/v1/product-catalog-service/products/**").hasRole(UserRole.ADMIN.roleName())
                            .pathMatchers(HttpMethod.DELETE, "/api/v1/product-catalog-service/products/**").hasRole(UserRole.ADMIN.roleName())

                            .pathMatchers(HttpMethod.POST, "/api/v1/product-catalog-service/categories/**").hasRole(UserRole.ADMIN.roleName())
                            .pathMatchers(HttpMethod.PUT, "/api/v1/product-catalog-service/categories/**").hasRole(UserRole.ADMIN.roleName())
                            .pathMatchers(HttpMethod.PATCH, "/api/v1/product-catalog-service/categories/**").hasRole(UserRole.ADMIN.roleName())
                            .pathMatchers(HttpMethod.DELETE, "/api/v1/product-catalog-service/categories/**").hasRole(UserRole.ADMIN.roleName())

                            .pathMatchers(HttpMethod.POST, "/api/v1/product-catalog-service/images/**").hasRole(UserRole.ADMIN.roleName())
                            .pathMatchers(HttpMethod.PUT, "/api/v1/product-catalog-service/images/**").hasRole(UserRole.ADMIN.roleName())
                            .pathMatchers(HttpMethod.PATCH, "/api/v1/product-catalog-service/images/**").hasRole(UserRole.ADMIN.roleName())
                            .pathMatchers(HttpMethod.DELETE, "/api/v1/product-catalog-service/images/**").hasRole(UserRole.ADMIN.roleName())

                            .pathMatchers(HttpMethod.POST, "/api/v1/product-catalog-service/variants/**").hasRole(UserRole.ADMIN.roleName())
                            .pathMatchers(HttpMethod.PUT, "/api/v1/product-catalog-service/variants/**").hasRole(UserRole.ADMIN.roleName())
                            .pathMatchers(HttpMethod.PATCH, "/api/v1/product-catalog-service/variants/**").hasRole(UserRole.ADMIN.roleName())
                            .pathMatchers(HttpMethod.DELETE, "/api/v1/product-catalog-service/variants/**").hasRole(UserRole.ADMIN.roleName())

                            // Deny all other unmatched product catalog service requests
                            .pathMatchers("/api/v1/product-catalog-service/**").denyAll()

                            // ========== SHOPPING CART SERVICE ==========
                            // Cart access for authenticated users
                            .pathMatchers("/api/v1/shopping-cart-service/cart/**").hasAnyRole(UserRole.ADMIN.roleName(), UserRole.CUSTOMER.roleName(), UserRole.MANAGER.roleName())
                            .pathMatchers("/api/v1/shopping-cart-service/carts/**").hasAnyRole(UserRole.ADMIN.roleName(), UserRole.CUSTOMER.roleName(), UserRole.MANAGER.roleName())

                            // ========== ORDER SERVICE ==========
                            // Customer personal orders
                            .pathMatchers("/api/v1/order-service/orders/my-orders/**").hasAnyRole(UserRole.ADMIN.roleName(), UserRole.CUSTOMER.roleName())

                            // Create orders (customers and admins)
                            .pathMatchers(HttpMethod.POST, "/api/v1/order-service/orders/checkout/*").hasAnyRole(UserRole.ADMIN.roleName(), UserRole.CUSTOMER.roleName())
                            .pathMatchers(HttpMethod.GET, "/api/v1/order-service/orders/checkout/*").hasAnyRole(UserRole.ADMIN.roleName(), UserRole.CUSTOMER.roleName())
                            .pathMatchers(HttpMethod.POST, "/api/v1/order-service/orders").hasAnyRole(UserRole.ADMIN.roleName(), UserRole.CUSTOMER.roleName())
                            .pathMatchers(HttpMethod.POST, "/api/v1/order-service/orders/").hasAnyRole(UserRole.ADMIN.roleName(), UserRole.CUSTOMER.roleName())

                            // Order management and viewing (admin, manager, support)
                            .pathMatchers(HttpMethod.GET, "/api/v1/order-service/orders/number/*").hasAnyRole(UserRole.ADMIN.roleName(), UserRole.CUSTOMER.roleName()) // Allow customers to view order by number
                            .pathMatchers(HttpMethod.GET, "/api/v1/order-service/orders/{id:\\d+}").hasAnyRole(UserRole.ADMIN.roleName(), UserRole.MANAGER.roleName(), UserRole.SUPPORT.roleName(), UserRole.CUSTOMER.roleName())
                            .pathMatchers(HttpMethod.GET, "/api/v1/order-service/orders").hasAnyRole(UserRole.ADMIN.roleName(), UserRole.MANAGER.roleName(), UserRole.SUPPORT.roleName())
                            .pathMatchers(HttpMethod.GET, "/api/v1/order-service/orders/user/**").hasAnyRole(UserRole.ADMIN.roleName(), UserRole.MANAGER.roleName(), UserRole.SUPPORT.roleName())
                            .pathMatchers(HttpMethod.PUT, "/api/v1/order-service/orders/**").hasAnyRole(UserRole.ADMIN.roleName(), UserRole.MANAGER.roleName())
                            .pathMatchers(HttpMethod.PATCH, "/api/v1/order-service/orders/**").hasAnyRole(UserRole.ADMIN.roleName(), UserRole.MANAGER.roleName())
                            .pathMatchers(HttpMethod.DELETE, "/api/v1/order-service/orders/**").hasAnyRole(UserRole.ADMIN.roleName(), UserRole.CUSTOMER.roleName()) // Need CUSTOMER to cancel order (DELETE /{orderId})

                            // ========== PAYMENT SERVICE ==========
                            // Customer personal payments
                            .pathMatchers("/api/v1/payment-service/payments/my-payments/**").hasAnyRole(UserRole.ADMIN.roleName(), UserRole.CUSTOMER.roleName())

                            // Process payments (customers and admins)
                            .pathMatchers(HttpMethod.POST, "/api/v1/payment-service/payments/checkout/*").hasAnyRole(UserRole.ADMIN.roleName(), UserRole.CUSTOMER.roleName())
                            .pathMatchers(HttpMethod.GET, "/api/v1/payment-service/payments/checkout/*").hasAnyRole(UserRole.ADMIN.roleName(), UserRole.CUSTOMER.roleName())
                            .pathMatchers(HttpMethod.POST, "/api/v1/payment-service/payments/process").hasAnyRole(UserRole.ADMIN.roleName(), UserRole.CUSTOMER.roleName())
                            .pathMatchers(HttpMethod.POST, "/api/v1/payment-service/payments/confirm").hasAnyRole(UserRole.ADMIN.roleName(), UserRole.CUSTOMER.roleName())

                            // Payment management (admin, manager)
                            .pathMatchers("/api/v1/payment-service/payments/**").hasAnyRole(UserRole.ADMIN.roleName(), UserRole.MANAGER.roleName())

                            // Public provider callbacks; all other webhook requests are denied.
                            .pathMatchers(HttpMethod.POST, "/api/v1/payment-service/webhooks/stripe", "/api/v1/payment-service/webhooks/stripe/hosted").permitAll()
                            .pathMatchers(HttpMethod.GET, "/api/v1/payment-service/webhooks/vnpay/ipn", "/api/v1/payment-service/webhooks/vnpay/return").permitAll()
                            .pathMatchers("/api/v1/payment-service/webhooks/**").denyAll()

                            // ========== NOTIFICATION SERVICE ==========
                            // Send notifications (admin, support)
                            .pathMatchers(HttpMethod.POST,
                                    "/api/v1/notification-service/notifications/send-email",
                                    "/api/v1/notification-service/notifications/send-sms",
                                    "/api/v1/notification-service/notifications/send-both"
                            ).hasAnyRole(UserRole.ADMIN.roleName(), UserRole.SUPPORT.roleName())

                            // View notifications (authenticated users can see their own)
                            .pathMatchers(HttpMethod.GET, "/api/v1/notification-service/notifications/**").authenticated()
                            .pathMatchers(HttpMethod.PUT, "/api/v1/notification-service/notifications/**").authenticated()
                            .pathMatchers(HttpMethod.PATCH, "/api/v1/notification-service/notifications/**").authenticated()

                            // All other requests require authentication
                            .anyExchange().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(grantedAuthoritiesExtractor()))
                )
                .build();
    }

    @Bean
    public Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthoritiesExtractor() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter);
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:#{null}}") String issuerUri) {

        NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());

        if (StringUtils.hasText(issuerUri)) {
            validators.add(new JwtIssuerValidator(issuerUri));
        }

        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return jwtDecoder;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allow specific origins (configure based on your frontend)
        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:3000",  // React default
                "http://localhost:4200",  // Angular default
                "http://localhost:8080",  // Local development
                "http://localhost:*"      // Any localhost port
        ));

        // Allow specific methods
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));

        // Allow specific headers
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));

        // Expose headers that frontend might need
        configuration.setExposedHeaders(Arrays.asList(
                "Access-Control-Allow-Origin",
                "Access-Control-Allow-Credentials",
                "Authorization"
        ));

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // Cache preflight response for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
