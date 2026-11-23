package org.de013.orderservice.integration;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.de013.orderservice.service.impl.AllocationRecovery;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = {"spring.cloud.config.enabled=false", "spring.cloud.bus.enabled=false", "eureka.client.enabled=false", "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true", "spring.jpa.open-in-view=false", "order.checkout-expiry.enabled=false", "order.allocation.recovery-delay-ms=3600000"})
class AllocationRecoveryFeignTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");
    static final List<String> requests = new CopyOnWriteArrayList<>();
    static final AtomicInteger mode = new AtomicInteger();
    static final AtomicInteger gets = new AtomicInteger();
    static UUID operation;
    static final HttpServer server = start();

    static HttpServer start() {
        try {
            var s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.createContext("/token", e -> {
                byte[] body = "{\"access_token\":\"test-service\",\"token_type\":\"Bearer\",\"expires_in\":3600}".getBytes(StandardCharsets.UTF_8);
                e.getResponseHeaders().add("Content-Type", "application/json");
                e.sendResponseHeaders(200, body.length); e.getResponseBody().write(body); e.close();
            });
            s.createContext("/internal/inventory-reservations", e -> {
                String method = e.getRequestMethod();
                String body = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                requests.add(method + " " + e.getRequestURI().getPath() + " " + body);
                int code = method.equals("GET") ? (mode.get() == 2 ? 500 : gets.incrementAndGet() == 1 ? 404 : 200) : mode.get() == 1 ? 409 : 200;
                byte[] response = ("{\"operationId\":\"" + operation + "\",\"targetKind\":\"PRODUCT\",\"targetId\":1,\"quantity\":1,\"owner\":\"owner\",\"state\":\"RESERVED\"}").getBytes(StandardCharsets.UTF_8);
                e.getResponseHeaders().add("Content-Type", "application/json");
                e.sendResponseHeaders(code, response.length); e.getResponseBody().write(response); e.close();
            });
            s.start(); return s;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.driver-class-name", DB::getDriverClassName);
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        r.add("spring.cloud.openfeign.client.config.inventoryReservation.url", () -> url);
        r.add("spring.security.oauth2.client.registration.catalog-inventory.client-id", () -> "order-service");
        r.add("spring.security.oauth2.client.registration.catalog-inventory.client-secret", () -> "test-only");
        r.add("spring.security.oauth2.client.registration.catalog-inventory.authorization-grant-type", () -> "client_credentials");
        r.add("spring.security.oauth2.client.provider.catalog-inventory.token-uri", () -> url + "/token");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired AllocationRecovery recovery;
    @AfterAll static void stop() { server.stop(0); }

    @Test void lookupNotFoundReservesAndConflictReadsBackWhileFailureDefers() {
        for (int scenario = 0; scenario < 3; scenario++) {
            mode.set(scenario); gets.set(0); requests.clear(); operation = UUID.randomUUID();
            long id = jdbc.queryForObject("INSERT INTO orders(order_number,user_id,status,order_type,created_at,updated_at,total_amount,currency,allocation_phase,shipping_first_name,shipping_last_name,shipping_street_address,shipping_city,shipping_state,shipping_postal_code,shipping_country) VALUES (?, 'owner', 'PENDING', 'STANDARD', CURRENT_TIMESTAMP,CURRENT_TIMESTAMP, 12.50, 'USD', 'ALLOCATING', 'Test','Owner','Street','City','State','12345','US') RETURNING id", Long.class, UUID.randomUUID().toString());
            jdbc.update("INSERT INTO inventory_allocation_intents(operation_id,order_id,target_kind,target_id,quantity,owner_id,state) VALUES (?,?,'PRODUCT',1,1,'owner','PENDING')", operation, id);
            recovery.process(id);
            assertEquals(scenario == 2 ? "ALLOCATING" : "READY", jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?", String.class, id));
            assertEquals(scenario == 2 ? "PENDING" : "RESERVED", jdbc.queryForObject("SELECT state FROM inventory_allocation_intents WHERE operation_id=?", String.class, operation));
            assertEquals(scenario == 2 ? 1 : scenario == 1 ? 3 : 2, requests.size());
            assertTrue(requests.get(0).startsWith("GET /internal/inventory-reservations/" + operation));
            if (scenario != 2) {
                assertTrue(requests.get(1).startsWith("POST /internal/inventory-reservations "));
                assertTrue(requests.get(1).contains(operation.toString()));
                if (scenario == 1) assertTrue(requests.get(2).startsWith("GET /internal/inventory-reservations/" + operation));
            }
        }
    }
}
