package org.de013.userservice.config;

import lombok.RequiredArgsConstructor;
import org.de013.userservice.security.HeaderAuthenticationFilter;
import org.de013.userservice.security.HeaderAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for User Service
 * <p>
 * Architecture: API Gateway-First with Keycloak
 * - All requests come through API Gateway (validates JWT with Keycloak)
 * - API Gateway forwards user context via headers (X-User-Id = Keycloak UUID)
 * - Service trusts internal network and reads user context from headers
 * - No authentication logic here - Keycloak handles authentication
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final HeaderAuthenticationFilter headerAuthenticationFilter;

    @Bean
    public HeaderAuthenticationProvider headerAuthenticationProvider() {
        return new HeaderAuthenticationProvider();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF Protection - Disable for stateless JWT
                .csrf(AbstractHttpConfigurer::disable)

                // Browser CORS is handled by API Gateway, not this internal service
                .cors(AbstractHttpConfigurer::disable)

                // Trust internal network - API Gateway handles authorization for client traffic
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v1/api-docs/**", "/swagger-resources/**", "/webjars/**").permitAll()
                        .requestMatchers("/internal/keycloak/users").permitAll() // Authenticated via X-Profile-Provisioner-Secret in service
                        .anyRequest().permitAll()
                )

                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(headerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authenticationProvider(headerAuthenticationProvider());

        return http.build();
    }

}
