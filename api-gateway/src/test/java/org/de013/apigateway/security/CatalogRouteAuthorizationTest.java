package org.de013.apigateway.security;

import org.de013.apigateway.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

class CatalogRouteAuthorizationTest {

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

    @ParameterizedTest
    @ValueSource(strings = {
            "/products",
            "/products/1",
            "/products/sku/IPHONE-15-PRO-128",
            "/products/featured",
            "/categories",
            "/categories/1",
            "/categories/tree",
            "/categories/slug/smartphones",
            "/categories/6/products",
            "/search?query=iphone",
            "/search/suggestions?query=iphone",
            "/images/1",
            "/variants/1",
            "/variants/sku/IPHONE-15-PRO-128",
            "/products/1/inventory",
            "/products/1/inventory/check?quantity=10",
            "/products/1/variants/inventory"
    })
    void anonymousCanAccessPublicReadEndpoints(String path) {
        client.get().uri(BASE + path).exchange().expectStatus().isOk();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/products/1/inventory/add?quantity=5",
            "/products/1/inventory/remove?quantity=5",
            "/products/1/inventory/set?quantity=10",
            "/variants/1/inventory/add?quantity=5",
            "/variants/1/inventory/remove?quantity=5",
            "/variants/1/inventory/set?quantity=10"
    })
    void stockAdjustmentsAllowedForAdminAndManager_DeniedForCustomerAndAnonymous(String path) {
        // Anonymous -> 401
        client.post().uri(BASE + path).exchange().expectStatus().isUnauthorized();

        // Customer & Support -> 403
        for (String role : List.of("CUSTOMER", "SUPPORT")) {
            client.post().uri(BASE + path)
                    .headers(headers -> headers.setBearerAuth(role))
                    .exchange().expectStatus().isForbidden();
        }

        // Admin & Manager -> 200
        for (String role : List.of("ADMIN", "MANAGER")) {
            client.post().uri(BASE + path)
                    .headers(headers -> headers.setBearerAuth(role))
                    .exchange().expectStatus().isOk();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/products/1/inventory/reserve?quantity=5",
            "/products/1/inventory/release?quantity=5",
            "/products/1/inventory/fulfill?quantity=5",
            "/variants/1/inventory/reserve?quantity=5",
            "/variants/1/inventory/release?quantity=5",
            "/variants/1/inventory/fulfill?quantity=5"
    })
    void orderStockOperationsDeniedForEveryoneIncludingAdmin(String path) {
        // Denied even for Admin (browser gateway access blocked for private order workflow)
        for (String role : List.of("ADMIN", "MANAGER", "CUSTOMER", "SUPPORT")) {
            client.post().uri(BASE + path)
                    .headers(headers -> headers.setBearerAuth(role))
                    .exchange().expectStatus().isForbidden();
        }
    }

    @Test
    void metadataMutationsAllowedOnlyForAdmin() {
        List<String> postPaths = List.of(
                "/products",
                "/categories",
                "/images",
                "/variants"
        );

        for (String path : postPaths) {
            // Anonymous -> 401
            client.post().uri(BASE + path).exchange().expectStatus().isUnauthorized();

            // Manager, Customer, Support -> 403
            for (String role : List.of("MANAGER", "CUSTOMER", "SUPPORT")) {
                client.post().uri(BASE + path)
                        .headers(headers -> headers.setBearerAuth(role))
                        .exchange().expectStatus().isForbidden();
            }

            // Admin -> 200
            client.post().uri(BASE + path)
                    .headers(headers -> headers.setBearerAuth("ADMIN"))
                    .exchange().expectStatus().isOk();
        }
    }

    @Test
    void unknownCatalogPathDeniedByDefault() {
        client.get().uri(BASE + "/unknown-internal-resource")
                .headers(headers -> headers.setBearerAuth("CUSTOMER"))
                .exchange().expectStatus().isForbidden();
    }
}
