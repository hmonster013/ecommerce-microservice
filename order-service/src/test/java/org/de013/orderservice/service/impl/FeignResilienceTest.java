package org.de013.orderservice.service.impl;

import com.sun.net.httpserver.HttpServer;
import feign.FeignException;
import feign.MethodHandlerConfiguration;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.Proxy;
import java.util.Map;
import feign.Retryer;
import org.de013.orderservice.client.CartClient;
import org.de013.orderservice.client.CartServiceClient;
import org.de013.orderservice.client.ProductCatalogClient;
import org.de013.orderservice.client.PaymentClosureClient;
import org.de013.orderservice.config.FeignConfig;
import org.de013.orderservice.client.InventoryReservationClient;
import org.de013.orderservice.config.FeignResilienceConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = FeignResilienceTest.App.class, properties = "spring.flyway.enabled=false")
class FeignResilienceTest {
    @Configuration
    @EnableAutoConfiguration
    @EnableFeignClients(clients = {InventoryReservationClient.class, PaymentClosureClient.class,
            PaymentReconciliationClient.class, CartClient.class, CartServiceClient.class, ProductCatalogClient.class})
    @Import({FeignResilienceConfig.class, FeignResilienceConfig.RethrowFallbackFactory.class, FeignConfig.class})
    static class App {
        @Bean feign.RequestInterceptor callerThreadCapture() { return template -> caller.set(Thread.currentThread()); }
    }

    static final AtomicInteger status = new AtomicInteger(404);
    static final AtomicInteger calls = new AtomicInteger();
    static final AtomicInteger disconnect = new AtomicInteger();
    static final AtomicInteger delay = new AtomicInteger();
    static final AtomicReference<Thread> caller = new AtomicReference<>();
    static final HttpServer server = server();
    static HttpServer server() {
        try {
            var s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.createContext("/token", e -> {
                byte[] body = "{\"access_token\":\"test-service\",\"token_type\":\"Bearer\",\"expires_in\":3600}".getBytes();
                e.getResponseHeaders().add("Content-Type", "application/json");
                e.sendResponseHeaders(200, body.length); e.getResponseBody().write(body); e.close();
            });
            s.createContext("/internal/inventory-reservations", e -> {
                calls.incrementAndGet();
                if (disconnect.get() == 1) { e.close(); return; }
                e.sendResponseHeaders(status.get(), -1); e.close();
            });
            s.createContext("/", e -> {
                if (delay.get() != 0) try { Thread.sleep(delay.get()); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                e.sendResponseHeaders(status.get(), -1); e.close();
            });
            s.start(); return s;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        p.add("spring.cloud.openfeign.client.config.inventoryReservation.url", () -> url);
        for (String id : new String[]{"paymentClosure", "paymentReconciliation", "orderCart", "shopping-cart-service", "product-catalog-service"})
            p.add("spring.cloud.openfeign.client.config." + id + ".url", () -> url);
        p.add("spring.security.oauth2.client.registration.catalog-inventory.client-id", () -> "order-service");
        p.add("spring.security.oauth2.client.registration.catalog-inventory.client-secret", () -> "test-only");
        p.add("spring.security.oauth2.client.registration.catalog-inventory.authorization-grant-type", () -> "client_credentials");
        p.add("spring.security.oauth2.client.provider.catalog-inventory.token-uri", () -> url + "/token");
        for (String id : new String[]{"payment-closure", "payment-reconciliation"}) {
            p.add("spring.security.oauth2.client.registration." + id + ".client-id", () -> "order-service");
            p.add("spring.security.oauth2.client.registration." + id + ".client-secret", () -> "test-only");
            p.add("spring.security.oauth2.client.registration." + id + ".authorization-grant-type", () -> "client_credentials");
            p.add("spring.security.oauth2.client.provider." + id + ".token-uri", () -> url + "/token");
        }
    }
    @Autowired InventoryReservationClient inventory;
    @Autowired PaymentClosureClient closure;
    @Autowired PaymentReconciliationClient reconciliation;
    @Autowired CartClient cart;
    @Autowired CartServiceClient legacyCart;
    @Autowired ProductCatalogClient catalog;
    @Autowired CircuitBreakerRegistry registry;
    @AfterAll static void stop() { server.stop(0); }

    private static Object field(Object object, String name) throws Exception {
        var f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }

    @Test void allRealProxiesUseBoundedOptionsAndNeverRetry() throws Exception {
        for (Object proxy : new Object[]{inventory, closure, reconciliation, cart, legacyCart, catalog}) {
            var dispatch = (Map<?, ?>) field(Proxy.getInvocationHandler(proxy), "dispatch");
            assertFalse(dispatch.isEmpty());
            for (Object handler : dispatch.values()) {
                var configuration = (MethodHandlerConfiguration) field(handler, "methodHandlerConfiguration");
                var options = configuration.getOptions();
                assertEquals(5000, options.connectTimeoutMillis());
                assertEquals(5000, options.readTimeoutMillis());
                assertSame(Retryer.NEVER_RETRY, configuration.getRetryer());
            }
        }
    }

    @Test void reconciliation404AndSynchronousDelayedCall() {
        status.set(404);
        for (int i = 0; i < 6; i++) assertThrows(FeignException.NotFound.class, () -> reconciliation.read(1L));
        assertEquals(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED,
                registry.circuitBreaker("PaymentReconciliationClientread").getState());
        status.set(204); delay.set(1200);
        try {
            cart.getCart(1L);
            assertSame(Thread.currentThread(), caller.get());
        } finally { delay.set(0); }
    }

    @Test void realProxyPreservesNotFoundAndOpensOnFailures() {
        status.set(404); disconnect.set(0); calls.set(0);
        for (int i = 0; i < 6; i++) assertThrows(FeignException.NotFound.class, () -> inventory.lookup(UUID.randomUUID()));
        assertEquals(6, calls.get());
        status.set(500);
        for (int i = 0; i < 5; i++) assertThrows(FeignException.class, () -> inventory.lookup(UUID.randomUUID()));
        assertThrows(CallNotPermittedException.class, () -> inventory.lookup(UUID.randomUUID()));
        assertEquals(11, calls.get());
        assertEquals(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN,
                registry.circuitBreaker("InventoryReservationClientlookup").getState());
        var breaker = registry.circuitBreaker("InventoryReservationClientlookup");
        breaker.transitionToHalfOpenState();
        status.set(204);
        inventory.lookup(UUID.randomUUID()); inventory.lookup(UUID.randomUUID());
        assertEquals(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED, breaker.getState());
        inventory.lookup(UUID.randomUUID());
        assertEquals(14, calls.get());
        status.set(409);
        var request = new InventoryReservationClient.Request(UUID.randomUUID(), "PRODUCT", 1L, 1, "owner");
        for (int i = 0; i < 6; i++) assertThrows(FeignException.Conflict.class, () -> inventory.reserve(request));
        for (int i = 0; i < 6; i++) assertThrows(FeignException.Conflict.class, () -> inventory.release(request.operationId()));
        for (int i = 0; i < 6; i++) assertThrows(FeignException.Conflict.class, () -> inventory.fulfill(request.operationId()));
        status.set(404);
        for (int i = 0; i < 6; i++) assertThrows(FeignException.NotFound.class, () -> inventory.release(request.operationId()));
        for (int i = 0; i < 6; i++) assertThrows(FeignException.NotFound.class, () -> inventory.fulfill(request.operationId()));
        int before = calls.get();
        disconnect.set(1);
        assertThrows(FeignException.class, () -> inventory.reserve(request));
        assertEquals(before + 1, calls.get());
    }
}
