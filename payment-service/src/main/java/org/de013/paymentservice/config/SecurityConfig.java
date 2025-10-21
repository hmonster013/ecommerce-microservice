package org.de013.paymentservice.config;

import lombok.RequiredArgsConstructor;
import org.de013.common.constant.ApiPaths;
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
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final HeaderAuthenticationFilter headerAuthenticationFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(authz -> authz
                        // Public endpoints
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**").permitAll()

                        // Webhook endpoints - public for payment gateway callbacks
                        .requestMatchers(ApiPaths.WEBHOOKS + "/**").permitAll()

                        // Stripe endpoints - public for health checks and integration testing
                        .requestMatchers(ApiPaths.STRIPE + ApiPaths.HEALTH, ApiPaths.STRIPE + ApiPaths.WEBHOOKS).permitAll()

                        // Payment processing endpoints - require authentication
                        .requestMatchers(ApiPaths.PAYMENTS + "/**", ApiPaths.PAYMENT_METHODS + "/**", ApiPaths.REFUNDS + "/**").authenticated()
                        .requestMatchers(ApiPaths.STRIPE + "/**").authenticated()

                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Add header authentication filter to process user context from API Gateway
                .addFilterBefore(headerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }


}
