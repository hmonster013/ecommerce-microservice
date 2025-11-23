package org.de013.apigateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class KeycloakUserContextFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(principal -> principal instanceof Authentication)
                .cast(Authentication.class)
                .flatMap(authentication -> {
                    if (authentication.getPrincipal() instanceof Jwt jwt) {
                        String keycloakId = jwt.getClaimAsString("sub");
                        String email = jwt.getClaimAsString("email");
                        String username = jwt.getClaimAsString("preferred_username");

                        log.debug("Forwarding Keycloak user context - ID: {}, Username: {}, Email: {}", keycloakId, username, email);

                        ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                            @Override
                            public HttpHeaders getHeaders() {
                                HttpHeaders headers = new HttpHeaders();
                                headers.putAll(super.getHeaders());
                                if (keycloakId != null) {
                                    headers.set("X-User-Id", keycloakId);
                                }
                                if (username != null) {
                                    headers.set("X-User-Username", username);
                                }
                                if (email != null) {
                                    headers.set("X-User-Email", email);
                                }
                                return headers;
                            }
                        };

                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    }
                    return chain.filter(exchange);
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        return -50;
    }
}
