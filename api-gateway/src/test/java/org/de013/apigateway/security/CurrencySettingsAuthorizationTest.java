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

class CurrencySettingsAuthorizationTest {
    private static final String BASE = "/api/v1/product-catalog-service";
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        ReactiveJwtDecoder decoder = token -> Mono.just(Jwt.withTokenValue(token).header("alg", "none")
                .subject("test-user").claim("realm_access", Map.of("roles", List.of(token))).build());
        ServerHttpSecurity http = ServerHttpSecurity.http()
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtDecoder(decoder)));
        var chain = new SecurityConfig().securityWebFilterChain(http);
        client = WebTestClient.bindToWebHandler(exchange -> exchange.getResponse().setComplete())
                .webFilter(new WebFilterChainProxy(chain)).build();
    }

    @Test
    void anonymousCanReadPublicRatesButNotSettings() {
        client.get().uri(BASE + "/exchange-rates").exchange().expectStatus().isOk();
        client.get().uri(BASE + "/settings/currency").exchange().expectStatus().isUnauthorized();
        client.put().uri(BASE + "/settings/currency").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void onlyAdminCanReadAndWriteSettings() {
        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.PUT)) {
            client.method(method).uri(BASE + "/settings/currency")
                    .headers(headers -> headers.setBearerAuth("ADMIN"))
                    .exchange().expectStatus().isOk();
            for (String role : List.of("MANAGER", "CUSTOMER", "SUPPORT")) {
                client.method(method).uri(BASE + "/settings/currency")
                        .headers(headers -> headers.setBearerAuth(role))
                        .exchange().expectStatus().isForbidden();
            }
        }
    }
}
