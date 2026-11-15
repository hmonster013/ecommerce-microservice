package org.de013.paymentservice.service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.de013.paymentservice.repository.PaymentRepository;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = {"spring.cloud.bus.enabled=false", "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"})
@ActiveProfiles("test")
class CheckoutClosureServiceTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", DB::getJdbcUrl);
        p.add("spring.datasource.username", DB::getUsername);
        p.add("spring.datasource.password", DB::getPassword);
        p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
    @Autowired CheckoutClosureService closures;
    @Autowired HostedCheckoutStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired PaymentRepository payments;
    @Autowired PaymentOrderGuard guard;
    @Autowired PlatformTransactionManager transactions;

    @Test void persistsAndReplaysMonotonicClosure() {
        long empty = 987001L, attempted = 987002L;
        UUID operation = UUID.randomUUID();
        assertEquals("SAFE", closures.close(empty, operation).status());
        assertEquals("SAFE", closures.close(empty, operation).status());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> closures.close(empty, UUID.randomUUID())).getStatusCode());
        var proof = jdbc.queryForMap("SELECT close_safe_proof, close_safe_at FROM payment_order_guard WHERE order_id = ?", empty);
        assertEquals("NO_PAYMENT_ATTEMPTS", proof.get("close_safe_proof"));
        assertNotNull(proof.get("close_safe_at"));
        closures.recordContradiction(empty);
        assertEquals("HOLD", closures.close(empty, operation).status());
        assertEquals(proof, jdbc.queryForMap("SELECT close_safe_proof, close_safe_at FROM payment_order_guard WHERE order_id = ?", empty));
        store.reserve(attempted, "owner", new BigDecimal("25.00"), "https://fixture.test/result");
        UUID second = UUID.randomUUID();
        assertEquals("HOLD", closures.close(attempted, second).status());
        assertEquals("HOLD", closures.close(attempted, second).status());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> closures.close(0L, second)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> closures.close(empty, null)).getStatusCode());
    }

    @Test void fenceSerializesReservationAndRecordsLateHostedTruth() throws Exception {
        long empty = 987010L, pending = 987011L;
        UUID operation = UUID.randomUUID();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var reservation = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                guard.requirePayable(empty);
                locked.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return null;
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            var closing = executor.submit(() -> closures.close(empty, operation));
            release.countDown();
            reservation.get(10, TimeUnit.SECONDS);
            assertEquals("SAFE", closing.get(10, TimeUnit.SECONDS).status());
            assertThrows(ResponseStatusException.class,
                    () -> store.reserve(empty, "owner", new BigDecimal("25.00"), "https://fixture.test/result"));

            var checkout = store.reserve(pending, "owner", new BigDecimal("25.00"), "https://fixture.test/result");
            UUID pendingOp = UUID.randomUUID();
            assertEquals("HOLD", closures.close(pending, pendingOp).status());
            assertThrows(ResponseStatusException.class, () -> store.create(checkout));
            store.record(pending, 0, "cs_late", "PAID", "pi_late");
            assertEquals(PaymentStatus.SUCCEEDED, payments.findById(checkout.getPaymentId()).orElseThrow().getStatus());
            assertEquals("HOLD", closures.close(pending, pendingOp).status());
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM payment_outbox WHERE payment_id = ?", Integer.class, checkout.getPaymentId()));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
