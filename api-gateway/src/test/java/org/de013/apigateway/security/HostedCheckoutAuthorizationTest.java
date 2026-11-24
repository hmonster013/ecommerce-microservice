package org.de013.apigateway.security;

import org.de013.apigateway.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
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

class HostedCheckoutAuthorizationTest {
    WebTestClient client;
    @BeforeEach void setup() {
        ReactiveJwtDecoder decoder = token -> Mono.just(Jwt.withTokenValue(token).header("alg", "none")
                .subject("owner").claim("realm_access", Map.of("roles", List.of(token))).build());
        var http = ServerHttpSecurity.http().oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtDecoder(decoder)));
        var chain = new SecurityConfig().securityWebFilterChain(http);
        client = WebTestClient.bindToWebHandler(exchange -> exchange.getResponse().setComplete())
                .webFilter(new WebFilterChainProxy(chain)).build();
    }

    @Test void customerAndAdminCanOnlyReachNewScopedCheckoutApis() {
        for (String path : List.of("/api/v1/order-service/orders/checkout/fixture", "/api/v1/payment-service/payments/checkout/1")) {
            for (var method : List.of(HttpMethod.GET, HttpMethod.POST)) {
                client.method(method).uri(path).exchange().expectStatus().isUnauthorized();
                for (String role : List.of("CUSTOMER", "ADMIN")) {
                    client.method(method).uri(path).headers(headers -> headers.setBearerAuth(role)).exchange().expectStatus().isOk();
                }
                for (String role : List.of("SUPPORT", "MANAGER")) {
                    client.method(method).uri(path).headers(headers -> headers.setBearerAuth(role)).exchange().expectStatus().isForbidden();
                }
            }
        }
    }

    @Test void customerCannotUseAdminPaymentOrOrderUpdateApis() {
        for (String path : List.of("/api/v1/payment-service/payments/1/status", "/api/v1/order-service/orders/1/mark-paid")) {
            client.put().uri(path).headers(headers -> headers.setBearerAuth("CUSTOMER")).exchange().expectStatus().isForbidden();
        }
    }
}
