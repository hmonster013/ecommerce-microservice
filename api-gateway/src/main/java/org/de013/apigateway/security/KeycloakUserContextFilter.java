package org.de013.apigateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

@Component
@Slf4j
public class KeycloakUserContextFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Step 1: Strip all client-supplied identity headers to prevent header spoofing
        HttpHeaders headers = new HttpHeaders();
        headers.addAll(exchange.getRequest().getHeaders());
        headers.remove("X-User-Id");
        headers.remove("X-User-Username");
        headers.remove("X-User-Email");
        headers.remove("X-User-Roles");

        // Step 2: If request is authenticated with JWT, inject trusted context
        return exchange.getPrincipal()
                .filter(principal -> principal instanceof Authentication)
                .cast(Authentication.class)
                .doOnNext(authentication -> {
                    if (authentication.getPrincipal() instanceof Jwt jwt) {
                        String keycloakId = jwt.getClaimAsString("sub");
                        String email = jwt.getClaimAsString("email");
                        String username = jwt.getClaimAsString("preferred_username");

                        if (keycloakId != null) {
                            headers.set("X-User-Id", keycloakId);
                        }
                        if (username != null) {
                            headers.set("X-User-Username", username);
                        }
                        if (email != null) {
                            headers.set("X-User-Email", email);
                        }

                        String roles = authentication.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .collect(Collectors.joining(","));
                        if (!roles.isBlank()) {
                            headers.set("X-User-Roles", roles);
                        }

                        log.debug("Injected trusted user context: ID={}, Username={}, Email={}, Roles={}",
                                keycloakId, username, email, roles);
                    }
                })
                .then(Mono.defer(() -> {
                    ServerHttpRequest request = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public HttpHeaders getHeaders() {
                            return HttpHeaders.readOnlyHttpHeaders(headers);
                        }
                    };
                    return chain.filter(exchange.mutate().request(request).build());
                }));
    }

    @Override
    public int getOrder() {
        return -50;
    }
}
