package org.de013.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.Arrays;

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
                        exchanges.pathMatchers("/actuator/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**", "/api/*/v3/api-docs/**", "/api/*/*/v3/api-docs/**").permitAll();
                    } else {
                        // Require authentication for all other paths
                        exchanges.pathMatchers("/actuator/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**", "/api/*/v3/api-docs/**", "/api/*/*/v3/api-docs/**").hasRole("ADMIN");
                    }

                    exchanges
                            // ========== AUTHENTICATION ENDPOINTS ==========
                            // Public authentication endpoints (login, register, refresh, logout)
                            .pathMatchers("/api/auth/**").permitAll()

                            // ========== USER SERVICE ==========
                            // Internal endpoints (service-to-service communication)
                            .pathMatchers("/api/user-service/api/v1/users/internal/**").permitAll()
                            .pathMatchers("/api/user-service/users/internal/**").permitAll() // Legacy path support

                            // Profile endpoints (authenticated users only)
                            .pathMatchers(HttpMethod.GET, "/api/user-service/users/profile").authenticated()
                            .pathMatchers(HttpMethod.PUT, "/api/user-service/users/profile").authenticated()

                            // Admin-only user management endpoints
                            .pathMatchers("/api/user-service/users/**").hasRole("ADMIN")

                            // ========== PRODUCT CATALOG SERVICE ==========
                            // Public read access to products and categories
                            .pathMatchers(HttpMethod.GET, "/api/product-catalog-service/products/**").permitAll()
                            .pathMatchers(HttpMethod.GET, "/api/product-catalog-service/categories/**").permitAll()

                            // Admin-only product management
                            .pathMatchers(HttpMethod.POST, "/api/product-catalog-service/products/**").hasRole("ADMIN")
                            .pathMatchers(HttpMethod.PUT, "/api/product-catalog-service/products/**").hasRole("ADMIN")
                            .pathMatchers(HttpMethod.PATCH, "/api/product-catalog-service/products/**").hasRole("ADMIN")
                            .pathMatchers(HttpMethod.DELETE, "/api/product-catalog-service/products/**").hasRole("ADMIN")

                            // Admin-only category management
                            .pathMatchers(HttpMethod.POST, "/api/product-catalog-service/categories/**").hasRole("ADMIN")
                            .pathMatchers(HttpMethod.PUT, "/api/product-catalog-service/categories/**").hasRole("ADMIN")
                            .pathMatchers(HttpMethod.PATCH, "/api/product-catalog-service/categories/**").hasRole("ADMIN")
                            .pathMatchers(HttpMethod.DELETE, "/api/product-catalog-service/categories/**").hasRole("ADMIN")

                            // Admin/Manager inventory management
                            .pathMatchers("/api/product-catalog-service/inventory/**").hasAnyRole("ADMIN", "MANAGER")

                            // ========== SHOPPING CART SERVICE ==========
                            // Cart access for authenticated users
                            .pathMatchers("/api/shopping-cart-service/cart/**").hasAnyRole("ADMIN", "CUSTOMER", "MANAGER")
                            .pathMatchers("/api/shopping-cart-service/carts/**").hasAnyRole("ADMIN", "CUSTOMER", "MANAGER")

                            // ========== ORDER SERVICE ==========
                            // Customer personal orders
                            .pathMatchers("/api/order-service/orders/my-orders/**").hasAnyRole("ADMIN", "CUSTOMER")

                            // Create orders (customers and admins)
                            .pathMatchers(HttpMethod.POST, "/api/order-service/orders").hasAnyRole("ADMIN", "CUSTOMER")
                            .pathMatchers(HttpMethod.POST, "/api/order-service/orders/").hasAnyRole("ADMIN", "CUSTOMER")

                            // Cancel own orders
                            .pathMatchers(HttpMethod.POST, "/api/order-service/orders/*/cancel").hasAnyRole("ADMIN", "CUSTOMER")
                            .pathMatchers(HttpMethod.PUT, "/api/order-service/orders/*/cancel").hasAnyRole("ADMIN", "CUSTOMER")

                            // Order management and viewing (admin, manager, support)
                            .pathMatchers(HttpMethod.GET, "/api/order-service/orders/**").hasAnyRole("ADMIN", "MANAGER", "SUPPORT", "CUSTOMER")
                            .pathMatchers(HttpMethod.PUT, "/api/order-service/orders/**").hasAnyRole("ADMIN", "MANAGER")
                            .pathMatchers(HttpMethod.PATCH, "/api/order-service/orders/**").hasAnyRole("ADMIN", "MANAGER")
                            .pathMatchers(HttpMethod.DELETE, "/api/order-service/orders/**").hasRole("ADMIN")

                            // ========== PAYMENT SERVICE ==========
                            // Customer personal payments
                            .pathMatchers("/api/payment-service/payments/my-payments/**").hasAnyRole("ADMIN", "CUSTOMER")

                            // Process payments (customers and admins)
                            .pathMatchers(HttpMethod.POST, "/api/payment-service/payments/process").hasAnyRole("ADMIN", "CUSTOMER")
                            .pathMatchers(HttpMethod.POST, "/api/payment-service/payments/confirm").hasAnyRole("ADMIN", "CUSTOMER")

                            // Payment management (admin, manager)
                            .pathMatchers("/api/payment-service/payments/**").hasAnyRole("ADMIN", "MANAGER")

                            // Webhooks (public - but should be validated by service)
                            .pathMatchers(HttpMethod.POST, "/api/payment-service/webhooks/**").permitAll()

                            // ========== NOTIFICATION SERVICE ==========
                            // Send notifications (admin, support)
                            .pathMatchers(HttpMethod.POST, "/api/notification-service/notifications/send/**").hasAnyRole("ADMIN", "SUPPORT")

                            // View notifications (authenticated users can see their own)
                            .pathMatchers(HttpMethod.GET, "/api/notification-service/notifications/**").authenticated()
                            .pathMatchers(HttpMethod.PUT, "/api/notification-service/notifications/**").authenticated()
                            .pathMatchers(HttpMethod.PATCH, "/api/notification-service/notifications/**").authenticated()

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
    public ReactiveJwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        return org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
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
