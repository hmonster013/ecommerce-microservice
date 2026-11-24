package org.de013.apigateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KeycloakUserContextFilterTest {

    private KeycloakUserContextFilter filter;

    @BeforeEach
    void setUp() {
        filter = new KeycloakUserContextFilter();
    }

    @Test
    void filter_WhenServerHeadersAreReadOnly_ShouldStripSpoofedHeadersWithoutMutatingOriginal() {
        HttpHeaders original = new HttpHeaders();
        original.add("X-User-Id", "spoofed-id");
        original.add("X-User-Roles", "ROLE_ADMIN");
        original.add("Accept", "application/json");
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getHeaders()).thenReturn(HttpHeaders.readOnlyHttpHeaders(original));
        when(request.getURI()).thenReturn(URI.create("http://localhost:8080/api/v1/user-service/v1/api-docs"));
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getBody()).thenReturn(reactor.core.publisher.Flux.empty());
        when(request.mutate()).thenCallRealMethod();
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build())
                .mutate().request(request).build();
        AtomicReference<ServerWebExchange> filtered = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();

        StepVerifier.create(filter.filter(exchange, ex -> {
            filtered.set(ex);
            calls.incrementAndGet();
            return Mono.empty();
        })).verifyComplete();

        assertEquals(1, calls.get());
        assertNull(filtered.get().getRequest().getHeaders().getFirst("X-User-Id"));
        assertNull(filtered.get().getRequest().getHeaders().getFirst("X-User-Roles"));
        assertEquals("application/json", filtered.get().getRequest().getHeaders().getFirst("Accept"));
        assertEquals(request.getURI(), filtered.get().getRequest().getURI());
        assertEquals("spoofed-id", original.getFirst("X-User-Id"));
    }

    @Test
    void filter_WhenUnauthenticatedAndHeadersSpoofed_ShouldStripIdentityHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/product-catalog-service/products")
                .header("X-User-Id", "spoofed-admin-id")
                .header("X-User-Username", "spoofed-admin")
                .header("X-User-Email", "spoofed@admin.com")
                .header("X-User-Roles", "ROLE_ADMIN")
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> filteredExchange = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        GatewayFilterChain chain = ex -> {
            filteredExchange.set(ex);
            calls.incrementAndGet();
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertNotNull(filteredExchange.get());
        assertEquals(1, calls.get());
        var mutatedHeaders = filteredExchange.get().getRequest().getHeaders();

        assertNull(mutatedHeaders.getFirst("X-User-Id"));
        assertNull(mutatedHeaders.getFirst("X-User-Username"));
        assertNull(mutatedHeaders.getFirst("X-User-Email"));
        assertNull(mutatedHeaders.getFirst("X-User-Roles"));
    }

    @Test
    void filter_WhenAuthenticated_ShouldStripSpoofedAndInjectTrustedClaims() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/order-service/orders")
                .header("X-User-Id", "fake-id")
                .header("X-User-Roles", "ROLE_ADMIN")
                .build();

        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of(
                        "sub", "verified-uuid-456",
                        "preferred_username", "valid_customer",
                        "email", "customer@example.com"
                )
        );

        Authentication auth = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );

        ServerWebExchange exchange = MockServerWebExchange.from(request)
                .mutate()
                .principal(Mono.just(auth))
                .build();

        AtomicReference<ServerWebExchange> filteredExchange = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        GatewayFilterChain chain = ex -> {
            filteredExchange.set(ex);
            calls.incrementAndGet();
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertNotNull(filteredExchange.get());
        assertEquals(1, calls.get());
        var mutatedHeaders = filteredExchange.get().getRequest().getHeaders();

        assertEquals("verified-uuid-456", mutatedHeaders.getFirst("X-User-Id"));
        assertEquals("valid_customer", mutatedHeaders.getFirst("X-User-Username"));
        assertEquals("customer@example.com", mutatedHeaders.getFirst("X-User-Email"));
        assertEquals("ROLE_CUSTOMER", mutatedHeaders.getFirst("X-User-Roles"));
    }
}
