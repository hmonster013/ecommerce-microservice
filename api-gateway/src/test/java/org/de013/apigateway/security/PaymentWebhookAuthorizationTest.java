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

class PaymentWebhookAuthorizationTest {
    private static final String BASE = "/api/v1/payment-service/webhooks/";
    private WebTestClient client;

    @BeforeEach void setup() {
        ReactiveJwtDecoder decoder = token -> Mono.just(Jwt.withTokenValue(token).header("alg", "none")
                .subject("owner").claim("realm_access", Map.of("roles", List.of(token))).build());
        var http = ServerHttpSecurity.http().oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtDecoder(decoder)));
        client = WebTestClient.bindToWebHandler(exchange -> exchange.getResponse().setComplete())
                .webFilter(new WebFilterChainProxy(new SecurityConfig().securityWebFilterChain(http))).build();
    }

    @Test void onlyExactCallbacksArePublicForTheirMethods() {
        for (String path : List.of("stripe", "stripe/hosted")) {
            client.post().uri(BASE + path).exchange().expectStatus().isOk();
            denied(HttpMethod.GET, path);
        }
        for (String path : List.of("vnpay/ipn", "vnpay/return")) {
            client.get().uri(BASE + path).exchange().expectStatus().isOk();
            denied(HttpMethod.POST, path);
        }
    }

    @Test void otherWebhookPathsAreDeniedEvenToAdmins() {
        for (String path : List.of("debug", "stripe/debug", "vnpay/debug", "stripe/hosted/extra", "vnpay/ipn/extra")) {
            for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.POST)) {
                denied(method, path);
            }
        }
    }

    private void denied(HttpMethod method, String path) {
        client.method(method).uri(BASE + path).exchange().expectStatus().isUnauthorized();
        client.method(method).uri(BASE + path).headers(headers -> headers.setBearerAuth("ADMIN"))
                .exchange().expectStatus().isForbidden();
    }
}
