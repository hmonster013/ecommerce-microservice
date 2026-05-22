package org.de013.userservice.config;

import lombok.RequiredArgsConstructor;
import org.de013.common.constant.ApiPaths;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

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

                // CORS Configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Trust internal network - API Gateway handles authorization
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**").permitAll()
                        .requestMatchers(ApiPaths.USERS + "/internal/**").permitAll()
                        .anyRequest().permitAll()
                )

                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(headerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authenticationProvider(headerAuthenticationProvider());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allow specific origins for development
        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:*",      // Any localhost port
                "http://127.0.0.1:*"       // Any 127.0.0.1 port
        ));

        // Allow specific methods
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));

        // Allow specific headers (including API Gateway headers)
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers",
                // API Gateway headers
                "X-User-Id",
                "X-User-Username",
                "X-User-Email",
                "X-User-Roles"
        ));

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
