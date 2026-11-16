package org.de013.apigateway.security;

import org.de013.apigateway.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

class PaymentConfirmAuthorizationTest {
    private WebTestClient client;
    private static final String CONFIRM = "/api/v1/payment-service/payments/123/confirm";

    @BeforeEach
    void setup() {
        ReactiveJwtDecoder decoder = token -> Mono.just(Jwt.withTokenValue(token).header("alg", "none")
                .subject("user").claim("realm_access", Map.of("roles", List.of(token))).build());
        var http = ServerHttpSecurity.http().oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtDecoder(decoder)));
        client = WebTestClient.bindToWebHandler(exchange -> exchange.getResponse().setComplete())
                .webFilter(new WebFilterChainProxy(new SecurityConfig().securityWebFilterChain(http))).build();
    }

    @Test
    void confirmRequiresAuthenticationAndAllowsCustomerAdminAndManager() {
        client.post().uri(CONFIRM).exchange().expectStatus().isUnauthorized();
        for (String role : List.of("CUSTOMER", "ADMIN", "MANAGER")) {
            client.post().uri(CONFIRM).headers(headers -> headers.setBearerAuth(role)).exchange().expectStatus().isOk();
        }
        client.post().uri(CONFIRM).headers(headers -> headers.setBearerAuth("SUPPORT")).exchange().expectStatus().isForbidden();
    }

    @Test
    void otherPaymentMethodsAndPathsKeepManagementRestrictions() {
        client.get().uri(CONFIRM).headers(headers -> headers.setBearerAuth("CUSTOMER")).exchange().expectStatus().isForbidden();
        client.post().uri("/api/v1/payment-service/payments/123/refund")
                .headers(headers -> headers.setBearerAuth("CUSTOMER")).exchange().expectStatus().isForbidden();
        client.get().uri(CONFIRM).headers(headers -> headers.setBearerAuth("MANAGER")).exchange().expectStatus().isOk();
        client.post().uri("/api/v1/payment-service/payments/123/refund")
                .headers(headers -> headers.setBearerAuth("ADMIN")).exchange().expectStatus().isOk();
    }
}
