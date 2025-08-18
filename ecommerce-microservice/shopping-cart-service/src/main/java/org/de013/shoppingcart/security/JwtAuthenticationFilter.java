package org.de013.shoppingcart.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter
 * Intercepts requests and validates JWT tokens
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenValidator jwtTokenValidator;
    private final CartSecurityService cartSecurityService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractTokenFromRequest(request);
            
            if (StringUtils.hasText(token) && jwtTokenValidator.validateToken(token)) {
                Authentication authentication = jwtTokenValidator.getAuthentication(token);
                
                if (authentication != null) {
                    // Additional validation for guest sessions
                    if (authentication instanceof JwtAuthenticationToken jwtAuth && jwtAuth.isGuest()) {
                        String sessionId = jwtAuth.getSessionId();
                        if (!cartSecurityService.isValidGuestSession(sessionId)) {
                            log.warn("Invalid guest session: {}", sessionId);
                            handleInvalidToken(response, "Invalid guest session");
                            return;
                        }
                    }
                    
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("Set authentication for user: {}", authentication.getName());
                }
            } else if (StringUtils.hasText(token)) {
                log.warn("Invalid JWT token received");
                handleInvalidToken(response, "Invalid token");
                return;
            }
            
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
            handleAuthenticationError(response, e.getMessage());
            return;
        }
        
        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from request
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        // Try Authorization header first
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        
        // Try query parameter as fallback
        String tokenParam = request.getParameter("token");
        if (StringUtils.hasText(tokenParam)) {
            return tokenParam;
        }
        
        return null;
    }

    /**
     * Handle invalid token
     */
    private void handleInvalidToken(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\",\"code\":\"INVALID_TOKEN\"}");
    }

    /**
     * Handle authentication error
     */
    private void handleAuthenticationError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Authentication failed: " + message + "\",\"code\":\"AUTH_ERROR\"}");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        
        // Skip authentication for public endpoints
        return path.startsWith("/actuator/") ||
               path.startsWith("/swagger-ui/") ||
               path.startsWith("/v3/api-docs/") ||
               path.equals("/api/v1/carts/guest") ||
               path.startsWith("/api/v1/auth/") ||
               path.startsWith("/public/");
    }
}
