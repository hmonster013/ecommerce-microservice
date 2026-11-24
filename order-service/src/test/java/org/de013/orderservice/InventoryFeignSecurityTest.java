package org.de013.orderservice;

import com.sun.net.httpserver.HttpServer;
import feign.FeignException;
import feign.Logger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.de013.orderservice.client.CartClient;
import org.de013.orderservice.client.InventoryReservationClient;
import org.de013.orderservice.config.FeignConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClientFactory;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = InventoryFeignSecurityTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "spring.flyway.enabled=false")
class InventoryFeignSecurityTest {
    @Configuration
    @EnableAutoConfiguration
    @EnableFeignClients(clients = {InventoryReservationClient.class, CartClient.class})
    @Import(FeignConfig.class)
    static class App { }

    static final AtomicInteger tokens = new AtomicInteger();
    static final AtomicInteger tokenStatus = new AtomicInteger(200);
    static final AtomicInteger inventoryStatus = new AtomicInteger(200);
    static final AtomicInteger expires = new AtomicInteger(3600);
    static final List<com.sun.net.httpserver.Headers> inventoryHeaders = new CopyOnWriteArrayList<>();
    static final List<com.sun.net.httpserver.Headers> cartHeaders = new CopyOnWriteArrayList<>();
    static final HttpServer server = createServer();

    static HttpServer createServer() {
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            http.createContext("/token", exchange -> {
                int sequence = tokens.incrementAndGet();
                byte[] body = (tokenStatus.get() == 200
                        ? "{\"access_token\":\"service-" + sequence + "\",\"token_type\":\"Bearer\",\"expires_in\":" + expires.get() + "}"
                        : "{\"error\":\"invalid_client\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(tokenStatus.get(), body.length);
                exchange.getResponseBody().write(body); exchange.close();
            });
            http.createContext("/internal/inventory-reservations", exchange -> {
                inventoryHeaders.add(exchange.getRequestHeaders());
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(inventoryStatus.get(), body.length);
                exchange.getResponseBody().write(body); exchange.close();
            });
            http.createContext("/carts", exchange -> {
                cartHeaders.add(exchange.getRequestHeaders());
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body); exchange.close();
            });
            http.start(); return http;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        p.add("spring.cloud.openfeign.client.config.inventoryReservation.url", () -> url);
        p.add("spring.cloud.openfeign.client.config.orderCart.url", () -> url);
        p.add("spring.security.oauth2.client.registration.catalog-inventory.client-id", () -> "order-service");
        p.add("spring.security.oauth2.client.registration.catalog-inventory.client-secret", () -> "ephemeral-test-only");
        p.add("spring.security.oauth2.client.registration.catalog-inventory.authorization-grant-type", () -> "client_credentials");
        p.add("spring.security.oauth2.client.provider.catalog-inventory.token-uri", () -> url + "/token");
    }

    @Autowired InventoryReservationClient inventory;
    @Autowired CartClient cart;
    @Autowired OAuth2AuthorizedClientService clients;
    @Autowired FeignClientFactory feign;

    @AfterEach void cleanup() {
        RequestContextHolder.resetRequestAttributes();
        clients.removeAuthorizedClient("catalog-inventory", "order-service");
        inventoryHeaders.clear(); cartHeaders.clear(); tokens.set(0); tokenStatus.set(200);
        inventoryStatus.set(200); expires.set(3600);
    }

    @AfterAll static void stopServer() { server.stop(0); }

    @Test void isolationReuseBackgroundAndOtherClient() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer user-token");
        request.addHeader("X-User-Id", "user-id");
        request.addHeader("X-User-Username", "user-name");
        request.addHeader("X-User-Email", "user@example.test");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        inventory.lookup(UUID.randomUUID());
        cart.getCart(1L);
        RequestContextHolder.resetRequestAttributes();
        inventory.lookup(UUID.randomUUID());
        assertEquals(1, tokens.get());
        for (var headers : inventoryHeaders) {
            assertEquals(List.of("Bearer service-1"), headers.get("Authorization"));
            assertNull(headers.getFirst("X-User-Id"));
            assertNull(headers.getFirst("X-User-Username"));
            assertNull(headers.getFirst("X-User-Email"));
            assertNull(headers.getFirst("X-User-Roles"));
        }
        assertEquals("Bearer user-token", cartHeaders.get(0).getFirst("Authorization"));
        assertEquals("user-id", cartHeaders.get(0).getFirst("X-User-Id"));
        assertEquals(Logger.Level.NONE, feign.getInstanceWithoutAncestors("inventoryReservation", Logger.Level.class));
    }

    @Test void expiredTokenIsReacquired() {
        expires.set(1); // Native provider considers tokens within its 60-second skew expired.
        inventory.lookup(UUID.randomUUID());
        inventory.lookup(UUID.randomUUID());
        assertEquals(2, tokens.get());
        assertEquals("Bearer service-2", inventoryHeaders.get(1).getFirst("Authorization"));
    }

    @Test void tokenFailureNeverCallsInventory() {
        tokenStatus.set(401);
        assertThrows(Exception.class, () -> inventory.lookup(UUID.randomUUID()));
        assertEquals(1, tokens.get());
        assertTrue(inventoryHeaders.isEmpty());
    }

    @Test void downstreamUnauthorizedIsNotRetried() {
        inventoryStatus.set(401);
        assertThrows(FeignException.Unauthorized.class, () -> inventory.lookup(UUID.randomUUID()));
        assertEquals(1, tokens.get());
        assertEquals(1, inventoryHeaders.size());
    }
}
