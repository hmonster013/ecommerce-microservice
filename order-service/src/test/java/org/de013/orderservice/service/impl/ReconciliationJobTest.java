package org.de013.orderservice.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.de013.orderservice.client.InventoryReservationClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {"spring.cloud.config.enabled=false", "spring.cloud.bus.enabled=false", "eureka.client.enabled=false", "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true", "order.checkout-expiry.enabled=false", "order.allocation.recovery-delay-ms=3600000", "order.reconciliation.enabled=true", "order.reconciliation.delay-ms=3600000"})
class ReconciliationJobTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ReconciliationJob job;
    @MockBean PaymentReconciliationClient payment;
    @MockBean InventoryReservationClient catalog;

    @Test void observesWithoutBusinessMutationAndDetectsConcurrentIntentChange() {
        long id = jdbc.queryForObject("INSERT INTO orders(order_number,user_id,status,order_type,allocation_phase,total_amount,currency,accepted_payment_id,accepted_payment_number) VALUES (?, 'owner','CANCELLED','STANDARD','CLOSURE_HOLD',12,'USD',2,'P2') RETURNING id", Long.class, UUID.randomUUID().toString());
        jdbc.update("INSERT INTO reconciliation_finding(order_id,next_check_at) VALUES (?,clock_timestamp()-interval '1 day')", id);
        UUID operation = UUID.randomUUID();
        jdbc.update("INSERT INTO inventory_allocation_intents VALUES (?,?, 'PRODUCT',3,1,'owner','RESERVED')", operation, id);
        when(payment.read(id)).thenAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            jdbc.update("UPDATE inventory_allocation_intents SET quantity=2 WHERE operation_id=?", operation);
            return new ReconciliationClassifier.PaymentSnapshot(id, Instant.now(), true, false, null, null, null, null, false,
                    List.of(new ReconciliationClassifier.Attempt(2L, "P2", "SUCCEEDED", new BigDecimal("12"), "USD", "gateway", true)));
        });
        when(catalog.lookup(operation)).thenAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new InventoryReservationClient.Reservation(operation, "PRODUCT", 3L, 1, "owner", "RELEASED", null);
        });
        job.run();
        String reasons = jdbc.queryForObject("SELECT reasons::text FROM reconciliation_revision WHERE order_id=?", String.class, id);
        for (var reason : List.of(org.de013.orderservice.entity.enums.ReconciliationReason.SUCCESS_WITH_CLOSED_OR_RELEASED,
                org.de013.orderservice.entity.enums.ReconciliationReason.HOLD_WITH_INVENTORY,
                org.de013.orderservice.entity.enums.ReconciliationReason.INVENTORY_MISMATCH,
                org.de013.orderservice.entity.enums.ReconciliationReason.PAYMENT_ORDER_MISMATCH,
                org.de013.orderservice.entity.enums.ReconciliationReason.UNKNOWN_OR_STALE)) assertTrue(reasons.contains(reason.name()), reasons);
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id));
        assertEquals("CLOSURE_HOLD", jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?", String.class, id));
        assertEquals(2, jdbc.queryForObject("SELECT quantity FROM inventory_allocation_intents WHERE operation_id=?", Integer.class, operation));
        verify(catalog, never()).release(any());
        verify(catalog, never()).reserve(any());
        verify(catalog, never()).fulfill(any());
    }

    @Test void paymentHttpFailuresPreserveDistinctEvidence() throws Exception {
        for (int code : List.of(404, 503)) {
            long id = jdbc.queryForObject("INSERT INTO orders(order_number,user_id,status,order_type) VALUES (?, 'owner','PENDING','STANDARD') RETURNING id", Long.class, UUID.randomUUID().toString());
            jdbc.update("INSERT INTO reconciliation_finding(order_id,next_check_at) VALUES (?,clock_timestamp()-interval '1 day')", id);
            var request = feign.Request.create(feign.Request.HttpMethod.GET, "/internal/reconciliation/orders/" + id, java.util.Map.of(), null, java.nio.charset.StandardCharsets.UTF_8, null);
            when(payment.read(id)).thenThrow(feign.FeignException.errorStatus("read", feign.Response.builder().status(code).reason("fixture").request(request).build()));
            job.run();
            var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(jdbc.queryForObject("SELECT evidence::text FROM reconciliation_revision WHERE order_id=? ORDER BY revision DESC LIMIT 1", String.class, id));
            assertEquals(code == 404 ? "MISSING" : "FAILED", json.path("paymentObservation").path("status").asText());
            assertEquals(code, json.path("paymentObservation").path("httpStatus").asInt());
            assertTrue(jdbc.queryForObject("SELECT reasons::text FROM reconciliation_revision WHERE order_id=? ORDER BY revision DESC LIMIT 1", String.class, id).contains(org.de013.orderservice.entity.enums.ReconciliationReason.UNKNOWN_OR_STALE.name()));
        }
    }

    @Test void boundedCursorReachesBeyondBatchDespiteFailure() {
        jdbc.update("UPDATE reconciliation_finding SET next_check_at=clock_timestamp()+interval '1 day'");
        long first = jdbc.queryForObject("SELECT coalesce(max(id),0) FROM orders", Long.class);
        for (int n = 0; n < 105; n++) {
            String status = n % 3 == 0 ? "CANCELLED" : n % 3 == 1 ? "PAID" : "ON_HOLD";
            jdbc.update("INSERT INTO orders(order_number,user_id,status,order_type,allocation_phase) VALUES (?, 'owner',?,'STANDARD',?)", UUID.randomUUID().toString(), status, n % 3 == 0 ? "CLOSED" : n % 3 == 1 ? "READY" : "CLOSURE_HOLD");
        }
        when(payment.read(anyLong())).thenThrow(new IllegalStateException("fixture unavailable"));
        for (int n = 0; n < 5; n++) job.run();
        assertEquals(105, jdbc.queryForObject("SELECT count(*) FROM reconciliation_revision WHERE order_id>?", Integer.class, first));
    }

    @Test void concurrentChangeAndExpiredWorkerAreGuarded() throws Exception {
        jdbc.update("UPDATE reconciliation_finding SET next_check_at=clock_timestamp()+interval '1 day'");
        long id = jdbc.queryForObject("INSERT INTO orders(order_number,user_id,status,order_type,allocation_phase) VALUES (?, 'owner','PENDING','STANDARD','READY') RETURNING id", Long.class, UUID.randomUUID().toString());
        jdbc.update("INSERT INTO reconciliation_finding(order_id,next_check_at) VALUES (?,clock_timestamp()-interval '1 day')", id);
        UUID operation = UUID.randomUUID();
        jdbc.update("INSERT INTO inventory_allocation_intents VALUES (?,?,'PRODUCT',3,1,'owner','RESERVED')", operation, id);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        when(payment.read(id)).thenAnswer(inv -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout");
            }
            return new ReconciliationClassifier.PaymentSnapshot(id, Instant.now(), true, false, null, null, null, null, false, List.of());
        });
        when(catalog.lookup(operation)).thenReturn(new InventoryReservationClient.Reservation(operation, "PRODUCT", 3L, 1, "owner", "RESERVED", null));
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var old = executor.submit(job::run);
            assertTrue(entered.await(10, java.util.concurrent.TimeUnit.SECONDS));
            jdbc.update("UPDATE inventory_allocation_intents SET quantity=2 WHERE operation_id=?", operation);
            release.countDown();
            old.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(jdbc.queryForObject("SELECT reasons::text FROM reconciliation_revision WHERE order_id=?", String.class, id).contains(org.de013.orderservice.entity.enums.ReconciliationReason.UNKNOWN_OR_STALE.name()));
            assertEquals(2, jdbc.queryForObject("SELECT quantity FROM inventory_allocation_intents WHERE operation_id=?", Integer.class, operation));
        } finally { release.countDown(); executor.shutdownNow(); }

        var blocked = new java.util.concurrent.CountDownLatch(1);
        var resume = new java.util.concurrent.CountDownLatch(1);
        var count = new java.util.concurrent.atomic.AtomicInteger();
        when(payment.read(id)).thenAnswer(inv -> {
            if (count.incrementAndGet() == 1) {
                blocked.countDown();
                if (!resume.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout");
            }
            return new ReconciliationClassifier.PaymentSnapshot(id, Instant.now(), true, false, null, null, null, null, false, List.of());
        });
        jdbc.update("UPDATE reconciliation_finding SET next_check_at=clock_timestamp()-interval '1 second' WHERE order_id=?", id);
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var stale = pool.submit(job::run);
            assertTrue(blocked.await(10, java.util.concurrent.TimeUnit.SECONDS));
            jdbc.update("UPDATE reconciliation_finding SET claim_until=clock_timestamp()-interval '1 second' WHERE order_id=?", id);
            job.run();
            String current = jdbc.queryForObject("SELECT evidence::text FROM reconciliation_revision WHERE order_id=? AND revision=2", String.class, id);
            resume.countDown();
            stale.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(2L, jdbc.queryForObject("SELECT current_revision FROM reconciliation_finding WHERE order_id=?", Long.class, id));
            assertEquals(current, jdbc.queryForObject("SELECT evidence::text FROM reconciliation_revision WHERE order_id=? AND revision=2", String.class, id));
            assertEquals("PENDING/READY", jdbc.queryForObject("SELECT status || '/' || allocation_phase FROM orders WHERE id=?", String.class, id));
        } finally { resume.countDown(); pool.shutdownNow(); }
    }
}
