package org.de013.userservice.config;

import lombok.RequiredArgsConstructor;
import org.de013.common.constant.ApiPaths;
import org.de013.userservice.security.HeaderAuthenticationFilter;
import org.de013.userservice.security.HeaderAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Security configuration for User Service
 *
 * This configuration is designed for API Gateway-First architecture:
 * - All requests should come through API Gateway (localhost:8080)
 * - API Gateway validates JWT and passes user context via headers
 * - Service uses HeaderAuthenticationFilter to read user context
 * - Direct service calls are not supported for security reasons
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final HeaderAuthenticationFilter headerAuthenticationFilter;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public HeaderAuthenticationProvider headerAuthenticationProvider() {
        return new HeaderAuthenticationProvider();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF Protection - Disable for stateless JWT
                .csrf(AbstractHttpConfigurer::disable)

                // CORS Configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Authorization Rules - Service manages its own security
                .authorizeHttpRequests(authz -> authz
                        // Public endpoints - No authentication required
                        .requestMatchers(ApiPaths.AUTH + "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**").permitAll()

                        // User endpoints - Require authentication, specific authorization via @PreAuthorize
                        .requestMatchers(ApiPaths.USERS + "/**").authenticated()

                        // Admin endpoints - Require ADMIN role
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // Health endpoints
                        .requestMatchers(ApiPaths.HEALTH).permitAll()

                        // All other requests require authentication
                        .anyRequest().authenticated()
                )

                // Session Management
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Add header authentication filter (for API Gateway calls only)
                .addFilterBefore(headerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // Authentication providers
                .authenticationProvider(headerAuthenticationProvider())
                .authenticationProvider(daoAuthenticationProvider());

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
