package org.de013.apigateway.security;

import org.de013.apigateway.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CatalogInternalAuthorizationTest {
    @Test
    void internalNamespaceNeverReachesDownstream() {
        AtomicInteger downstream = new AtomicInteger();
        ReactiveJwtDecoder decoder = token -> Mono.just(Jwt.withTokenValue(token).header("alg", "none")
                .subject("test-user").claim("realm_access", Map.of("roles", List.of(token))).build());
        ServerHttpSecurity http = ServerHttpSecurity.http()
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtDecoder(decoder)));
        var chain = new SecurityConfig().securityWebFilterChain(http);
        WebTestClient client = WebTestClient.bindToWebHandler(exchange -> {
            downstream.incrementAndGet();
            return exchange.getResponse().setComplete();
        }).webFilter(new WebFilterChainProxy(chain)).build();

        String base = "/api/v1/product-catalog-service/internal";
        String reservation = "/inventory-reservations/123e4567-e89b-12d3-a456-426614174000";
        for (String path : List.of("", "/", "/inventory-reservations", reservation,
                reservation + "/release", reservation + "/fulfill", "/future/nested")) {
            for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST, HttpMethod.PUT,
                    HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.OPTIONS)) {
                client.method(method).uri(base + path).exchange().expectStatus().isUnauthorized();
                for (String role : List.of("CUSTOMER", "ADMIN", "MANAGER")) {
                    client.method(method).uri(base + path).headers(headers -> headers.setBearerAuth(role))
                            .exchange().expectStatus().isForbidden();
                }
            }
            // Spring Security's default HTTP firewall rejects TRACE before authorization.
            client.method(HttpMethod.TRACE).uri(base + path).exchange().expectStatus().isBadRequest();
            for (String role : List.of("CUSTOMER", "ADMIN", "MANAGER")) {
                client.method(HttpMethod.TRACE).uri(base + path).headers(headers -> headers.setBearerAuth(role))
                        .exchange().expectStatus().isBadRequest();
            }
        }
        // The mock exchange rejects this preflight before forwarding it downstream.
        client.options().uri(base + reservation).header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "GET").exchange().expectStatus().isForbidden();
        assertEquals(0, downstream.get());
    }
}
