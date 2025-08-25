package org.de013.userservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.userservice.service.TokenBlacklistService;
import org.de013.common.util.HttpUtils;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                 @Qualifier("customUserDetailsService") UserDetailsService userDetailsService,
                                 TokenBlacklistService tokenBlacklistService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            String jwt = getJwtFromRequest(request);
            log.debug("Attempting authentication for request: {}", request.getRequestURI());

            if (!StringUtils.hasText(jwt)) {
                log.debug("JWT token not found in 'Authorization' header. Skipping filter.");
                filterChain.doFilter(request, response);
                return;
            }

            log.debug("Extracted JWT successfully.");

            if (jwtTokenProvider.validateToken(jwt)) {
                log.debug("JWT validation successful.");

                if (tokenBlacklistService.isTokenBlacklisted(jwt)) {
                    log.warn("Authentication failed: Token is blacklisted. User: {}, IP: {}",
                            jwtTokenProvider.getUsernameFromToken(jwt), HttpUtils.getClientIpAddress(request));
                } else {
                    String username = jwtTokenProvider.getUsernameFromToken(jwt);
                    log.debug("Username '{}' extracted from token.", username);

                    if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                        log.debug("SecurityContext is empty. Loading UserDetails for '{}'.", username);
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                        if (jwtTokenProvider.validateToken(jwt, userDetails)) {
                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            log.info("User '{}' authenticated successfully. SecurityContext updated.", username);
                        } else {
                            log.warn("Authentication failed: Token is valid but does not match user details for '{}'.", username);
                        }
                    } else {
                        log.debug("Skipping authentication: Username is null or SecurityContext already contains an authentication object.");
                    }
                }
            } else {
                log.warn("Authentication failed: JWT validation failed.");
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from Authorization header
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
