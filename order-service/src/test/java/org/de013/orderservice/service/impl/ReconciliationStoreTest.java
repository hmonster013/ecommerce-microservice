package org.de013.orderservice.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.UUID;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {"spring.cloud.config.enabled=false", "spring.cloud.bus.enabled=false", "eureka.client.enabled=false", "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true", "order.checkout-expiry.enabled=false", "order.allocation.recovery-delay-ms=3600000"})
class ReconciliationStoreTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ReconciliationStore store;

    @Test void metricsCountOnlyCurrentRevisionReasonsAndIncludeRevisionZero() {
        var registry = new SimpleMeterRegistry();
        new OrderObservability(registry, jdbc);
        var findings = registry.get("ecommerce.reconciliation.findings").gauge();
        var inventory = registry.get("ecommerce.reconciliation.reasons").tag("reason", "INVENTORY_MISMATCH").gauge();
        var stale = registry.get("ecommerce.reconciliation.reasons").tag("reason", "UNKNOWN_OR_STALE").gauge();
        var allocating = registry.get("ecommerce.saga.orders").tag("phase", "ALLOCATING").gauge();
        var failures = registry.get("ecommerce.saga.failures").gauge();
        double beforeFindings = findings.value(), beforeInventory = inventory.value(), beforeStale = stale.value();
        double beforeAllocating = allocating.value(), beforeFailures = failures.value();
        long id = jdbc.queryForObject("INSERT INTO orders(order_number,user_id,status,order_type,allocation_phase) VALUES (?, 'owner', 'PENDING', 'STANDARD', 'ALLOCATING') RETURNING id", Long.class, UUID.randomUUID().toString());
        long pending = jdbc.queryForObject("INSERT INTO orders(order_number,user_id,status,order_type,allocation_phase) VALUES (?, 'owner', 'FAILED', 'STANDARD', 'FAILED') RETURNING id", Long.class, UUID.randomUUID().toString());
        jdbc.update("INSERT INTO reconciliation_finding(order_id,current_revision,next_check_at) VALUES (?,2,clock_timestamp()+interval '1 day'), (?,0,clock_timestamp()+interval '1 day')", id, pending);
        jdbc.update("INSERT INTO reconciliation_revision(order_id,revision,evidence,reasons) VALUES (?,1,'{}','[\"UNKNOWN_OR_STALE\"]'), (?,2,'{}','[\"INVENTORY_MISMATCH\"]')", id, id);
        assertEquals(beforeFindings + 2, findings.value());
        assertEquals(beforeInventory + 1, inventory.value());
        assertEquals(beforeStale, stale.value());
        assertEquals(beforeAllocating + 1, allocating.value());
        assertEquals(beforeFailures + 1, failures.value());
    }

    @Test void leaseTakeoverHistoryAndRevisionCas() {
        jdbc.update("DELETE FROM reconciliation_finding WHERE current_revision=0");
        long id = jdbc.queryForObject("INSERT INTO orders(order_number,user_id,status,order_type) VALUES (?, 'owner', 'PENDING', 'STANDARD') RETURNING id", Long.class, UUID.randomUUID().toString());
        jdbc.update("INSERT INTO reconciliation_finding(order_id) VALUES (?)", id);
        var old = store.claim(1, Duration.ofMinutes(1)).get(0);
        jdbc.update("UPDATE reconciliation_finding SET claim_until=clock_timestamp()-interval '1 second' WHERE order_id=?", id);
        var current = store.claim(1, Duration.ofMinutes(1)).get(0);
        assertNotEquals(old.token(), current.token());
        assertFalse(store.complete(old, "{}", "[]", Duration.ZERO));
        assertTrue(store.complete(current, "{\"sample\":1}", "[\"UNKNOWN_OR_STALE\"]", Duration.ZERO));
        tx.executeWithoutResult(s -> {
            assertEquals(1L, jdbc.queryForObject("SELECT current_revision FROM reconciliation_finding WHERE order_id=? FOR UPDATE", Long.class, id));
            jdbc.update("INSERT INTO reconciliation_audit(order_id,revision,actor,note,action) VALUES (?,1,'operator','review','ACKNOWLEDGED')", id);
        });
        var next = store.claim(1, Duration.ofMinutes(1)).get(0);
        assertTrue(store.complete(next, "{\"sample\":2}", "[]", Duration.ofHours(1)));
        assertEquals(0, jdbc.update("INSERT INTO reconciliation_audit(order_id,revision,actor,note,action) SELECT order_id,current_revision,'operator','stale','ACKNOWLEDGED' FROM reconciliation_finding WHERE order_id=? AND current_revision=1", id));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM reconciliation_audit WHERE order_id=? AND revision=1", Integer.class, id));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM reconciliation_revision WHERE order_id=?", Integer.class, id));
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE reconciliation_audit SET note='changed' WHERE order_id=?", id));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM reconciliation_revision WHERE order_id=?", id));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id));
    }

    @Test void concurrentAuditAndRefreshSerializeOnFinding() throws Exception {
        long id = jdbc.queryForObject("INSERT INTO orders(order_number,user_id,status,order_type) VALUES (?, 'owner','PENDING','STANDARD') RETURNING id", Long.class, UUID.randomUUID().toString());
        jdbc.update("INSERT INTO reconciliation_finding(order_id,next_check_at) VALUES (?,clock_timestamp()-interval '1 day')", id);
        assertTrue(store.complete(store.claim(1, Duration.ofMinutes(1)).get(0), "{}", "[]", Duration.ZERO));
        var claim = store.claim(1, Duration.ofMinutes(1)).get(0);
        var locked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var audit = pool.submit(() -> tx.executeWithoutResult(s -> {
                assertEquals(1L, jdbc.queryForObject("SELECT current_revision FROM reconciliation_finding WHERE order_id=? FOR UPDATE", Long.class, id));
                locked.countDown();
                try { assertTrue(release.await(10, java.util.concurrent.TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                jdbc.update("INSERT INTO reconciliation_audit(order_id,revision,actor,note,action) VALUES (?,1,'operator','checked','ACKNOWLEDGED')", id);
            }));
            assertTrue(locked.await(10, java.util.concurrent.TimeUnit.SECONDS));
            var refresh = pool.submit(() -> store.complete(claim, "{}", "[]", Duration.ofHours(1)));
            release.countDown();
            audit.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(refresh.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM reconciliation_audit WHERE order_id=? AND revision=1", Integer.class, id));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM reconciliation_audit WHERE order_id=? AND revision=2", Integer.class, id));
            assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM reconciliation_audit WHERE order_id=?", id));
        } finally { release.countDown(); pool.shutdownNow(); }
    }
}
