package org.de013.orderservice.integration;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.de013.orderservice.client.InventoryReservationClient;
import org.de013.orderservice.client.PaymentClosureClient;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.entity.enums.OrderType;
import org.de013.orderservice.entity.valueobject.Address;
import org.de013.orderservice.entity.valueobject.Money;
import org.de013.orderservice.repository.OrderRepository;
import org.de013.orderservice.service.impl.CheckoutExpiryRecovery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(properties = {"spring.cloud.config.enabled=false", "spring.cloud.bus.enabled=false", "eureka.client.enabled=false", "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true", "spring.jpa.open-in-view=false", "order.checkout-expiry.enabled=false", "order.allocation.recovery-delay-ms=3600000"})
class CheckoutExpiryClaimTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.driver-class-name", DB::getDriverClassName);
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired OrderRepository orders;
    @Autowired org.de013.orderservice.service.impl.PaymentEventProcessor events;
    @Autowired org.de013.orderservice.service.OrderService orderService;

    private long order(String phase) {
        return jdbc.queryForObject("INSERT INTO orders(order_number,user_id,status,order_type,created_at,updated_at,total_amount,currency,allocation_phase,shipping_first_name,shipping_last_name,shipping_street_address,shipping_city,shipping_state,shipping_postal_code,shipping_country) VALUES (?, 'owner', 'PENDING', 'STANDARD', CURRENT_TIMESTAMP,CURRENT_TIMESTAMP, 12.50, 'USD', ?, 'Test','Owner','Street','City','State','12345','US') RETURNING id", Long.class, UUID.randomUUID().toString(), phase);
    }

    @Test void concurrentPaidAndExpirySerializeBothInterleavings() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            long paidFirst = order("READY");
            jdbc.update("UPDATE orders SET checkout_deadline=CURRENT_TIMESTAMP - interval '1 minute' WHERE id=?", paidFirst);
            var locked = new CountDownLatch(1);
            var commit = new CountDownLatch(1);
            var payment = mock(PaymentClosureClient.class);
            var inventory = mock(InventoryReservationClient.class);
            var recovery = new CheckoutExpiryRecovery(jdbc, tx, orders, payment, inventory);
            var paid = pool.submit(() -> tx.executeWithoutResult(s -> {
                orders.lockForPayment(paidFirst).orElseThrow();
                orderService.markOrderAsPaid(paidFirst, 201L, "P-201");
                locked.countDown();
                try { assertTrue(commit.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            var closeStarted = new CountDownLatch(1);
            var close = pool.submit(() -> { closeStarted.countDown(); recovery.process(paidFirst); });
            assertTrue(closeStarted.await(10, TimeUnit.SECONDS));
            commit.countDown(); paid.get(10, TimeUnit.SECONDS); close.get(10, TimeUnit.SECONDS);
            assertEquals("PAID", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, paidFirst));
            assertEquals("FULFILL_PENDING", jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?", String.class, paidFirst));
            verifyNoInteractions(payment, inventory);

            long closeFirst = order("READY");
            jdbc.update("UPDATE orders SET checkout_deadline=CURRENT_TIMESTAMP - interval '1 minute' WHERE id=?", closeFirst);
            var remoteEntered = new CountDownLatch(1);
            var remoteResume = new CountDownLatch(1);
            when(payment.close(eq(closeFirst), any())).thenAnswer(c -> {
                remoteEntered.countDown();
                assertTrue(remoteResume.await(10, TimeUnit.SECONDS));
                return new PaymentClosureClient.Result(closeFirst, ((PaymentClosureClient.Request)c.getArgument(1)).operationId(), "SAFE", null);
            });
            var closing = pool.submit(() -> recovery.process(closeFirst));
            assertTrue(remoteEntered.await(10, TimeUnit.SECONDS));
            UUID event = UUID.randomUUID();
            String raw = "{\"eventId\":\"" + event + "\",\"eventType\":\"PaymentSucceeded\",\"schemaVersion\":1,\"occurredAt\":\"2026-01-01T00:00:00Z\",\"paymentId\":202,\"paymentNumber\":\"P-202\",\"orderId\":" + closeFirst + ",\"provider\":\"STRIPE\",\"amount\":12.50,\"currency\":\"USD\"}";
            try {
                var received = pool.submit(() -> events.process(raw, Long.toString(closeFirst), "expiry-race", 0, 1));
                received.get(10, TimeUnit.SECONDS);
                assertEquals("HOLD", jdbc.queryForObject("SELECT result FROM payment_event_inbox WHERE event_id=?", String.class, event));
            } finally { remoteResume.countDown(); }
            closing.get(10, TimeUnit.SECONDS);
            assertEquals("CLOSURE_HOLD", jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?", String.class, closeFirst));
            assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, closeFirst));
            verifyNoInteractions(inventory);
        } finally { pool.shutdownNow(); }
    }

    @Test void latePaymentKafkaDuplicateAndSyncCommitHold() {
        long kafka = order("CLOSING"), sync = order("RELEASE_PENDING");
        UUID event = UUID.randomUUID();
        String raw = "{\"eventId\":\"" + event + "\",\"eventType\":\"PaymentSucceeded\",\"schemaVersion\":1,\"occurredAt\":\"2026-01-01T00:00:00Z\",\"paymentId\":101,\"paymentNumber\":\"P-101\",\"orderId\":" + kafka + ",\"provider\":\"STRIPE\",\"amount\":12.50,\"currency\":\"USD\"}";
        events.process(raw, Long.toString(kafka), "expiry", 0, 1);
        events.process(raw, Long.toString(kafka), "expiry", 0, 2);
        orderService.markOrderAsPaid(sync, 102L, "P-102");
        for (long id : new long[]{kafka, sync}) {
            assertEquals("CLOSURE_HOLD", jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?", String.class, id));
            assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id));
        }
        assertEquals("HOLD", jdbc.queryForObject("SELECT result FROM payment_event_inbox WHERE event_id=?", String.class, event));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM payment_event_inbox WHERE event_id=?", Integer.class, event));
    }

    @Test void currentHoldStopsSecondReleaseAndMismatchStopsAll() {
        long id = order("CLOSING"); UUID operation = UUID.randomUUID();
        jdbc.update("UPDATE orders SET checkout_close_operation=? WHERE id=?", operation, id);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        for (UUID line : new UUID[]{a,b}) jdbc.update("INSERT INTO inventory_allocation_intents(operation_id,order_id,target_kind,target_id,quantity,owner_id,state) VALUES (?,?,'PRODUCT',1,1,'owner','RESERVED')", line, id);
        var payment = mock(PaymentClosureClient.class); var inventory = mock(InventoryReservationClient.class);
        when(payment.close(eq(id), any())).thenReturn(new PaymentClosureClient.Result(id, operation, "SAFE", null), new PaymentClosureClient.Result(id, operation, "SAFE", null), new PaymentClosureClient.Result(id, operation, "HOLD", "LATE"));
        when(inventory.release(any())).thenAnswer(c -> new InventoryReservationClient.Reservation(c.getArgument(0), "PRODUCT", 1L, 1, "owner", "RELEASED", null));
        new CheckoutExpiryRecovery(jdbc, tx, orders, payment, inventory).process(id);
        assertEquals("CLOSURE_HOLD", jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?", String.class, id));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM inventory_allocation_intents WHERE order_id=? AND state='RELEASED'", Integer.class, id));
        verify(inventory, times(1)).release(any());
        long other = order("CLOSING"); UUID op2 = UUID.randomUUID();
        jdbc.update("UPDATE orders SET checkout_close_operation=? WHERE id=?", op2, other);
        when(payment.close(eq(other), any())).thenReturn(new PaymentClosureClient.Result(other, UUID.randomUUID(), "SAFE", null));
        clearInvocations(inventory);
        new CheckoutExpiryRecovery(jdbc, tx, orders, payment, inventory).process(other);
        assertEquals("CLOSURE_HOLD", jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?", String.class, other));
        verifyNoInteractions(inventory);
    }

    @Test void paymentRpcDoesNotHoldOrderLockAndLegacyReadyNeverExpires() {
        long id = order("READY"), legacy = order("READY");
        jdbc.update("UPDATE orders SET checkout_deadline=CURRENT_TIMESTAMP - interval '1 minute' WHERE id=?", id);
        var payment = mock(PaymentClosureClient.class); var inventory = mock(InventoryReservationClient.class);
        when(payment.close(eq(id), any())).thenAnswer(c -> {
            var executor = Executors.newSingleThreadExecutor();
            try { executor.submit(() -> jdbc.update("UPDATE orders SET checkout_close_hold_reason='concurrent' WHERE id=?", id)).get(5, TimeUnit.SECONDS); }
            finally { executor.shutdownNow(); }
            return new PaymentClosureClient.Result(id, ((PaymentClosureClient.Request)c.getArgument(1)).operationId(), "HOLD", "test");
        });
        var recovery = new CheckoutExpiryRecovery(jdbc, tx, orders, payment, inventory);
        recovery.process(id); recovery.process(legacy);
        assertEquals("CLOSURE_HOLD", jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?", String.class, id));
        assertEquals("READY", jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?", String.class, legacy));
        verify(payment, times(1)).close(anyLong(), any());
    }

    @Test void expiredClaimCanBeReclaimedAndStaleReleaseCannotOverwriteHold() throws Exception {
        var key = UUID.randomUUID();
        var address = Address.builder().firstName("Test").lastName("Owner").streetAddress("123 Test Street")
                .city("Hanoi").state("Hanoi").postalCode("100000").country("VN").build();
        var order = orders.saveAndFlush(Order.builder().checkoutKey(key.toString())
                .orderNumber("ORD-" + key.toString().substring(0, 8)).userId("owner")
                .status(OrderStatus.PENDING).orderType(OrderType.STANDARD)
                .totalAmount(Money.of(1.00, "USD")).subtotalAmount(Money.of(1.00, "USD"))
                .shippingAddress(address).build());
        long id = order.getId();
        UUID operation = UUID.randomUUID(), line = UUID.randomUUID();
        jdbc.update("UPDATE orders SET allocation_phase='CLOSING', checkout_close_operation=? WHERE id=?", operation, id);
        jdbc.update("INSERT INTO inventory_allocation_intents(operation_id,order_id,target_kind,target_id,quantity,owner_id,state) VALUES (?,?,'PRODUCT',1,1,'owner','RESERVED')", line, id);
        var payment = mock(PaymentClosureClient.class);
        var inventory = mock(InventoryReservationClient.class);
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        when(payment.close(eq(id), any())).thenReturn(new PaymentClosureClient.Result(id, operation, "SAFE", null),
                new PaymentClosureClient.Result(id, operation, "SAFE", null),
                new PaymentClosureClient.Result(id, operation, "HOLD", "LATE_PAYMENT"));
        when(inventory.release(line)).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(finish.await(10, TimeUnit.SECONDS));
            return new InventoryReservationClient.Reservation(line, "PRODUCT", 1L, 1, "owner", "RELEASED", null);
        });
        var first = new CheckoutExpiryRecovery(jdbc, tx, orders, payment, inventory);
        var second = new CheckoutExpiryRecovery(jdbc, tx, orders, payment, inventory);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var pending = executor.submit(() -> first.process(id));
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            second.process(id);
            verify(payment, times(2)).close(eq(id), any());
            jdbc.update("UPDATE orders SET checkout_close_claim_until=CURRENT_TIMESTAMP - interval '1 second' WHERE id=?", id);
            second.process(id);
            finish.countDown();
            pending.get(10, TimeUnit.SECONDS);
            assertEquals("CLOSURE_HOLD", jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?", String.class, id));
            assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id));
            assertNull(jdbc.queryForObject("SELECT cancelled_at FROM orders WHERE id=?", java.sql.Timestamp.class, id));
            assertEquals("RESERVED", jdbc.queryForObject("SELECT state FROM inventory_allocation_intents WHERE operation_id=?", String.class, line));
            assertNotNull(jdbc.queryForObject("SELECT checkout_close_safe_at FROM orders WHERE id=?", java.sql.Timestamp.class, id));
            verify(inventory, times(1)).release(line);
        } finally {
            finish.countDown();
            executor.shutdownNow();
            jdbc.update("DELETE FROM inventory_allocation_intents WHERE order_id=?", id);
            orders.deleteById(id);
        }
    }

    @Test void allReleasedClosesAndCancelsExpiredOrder() {
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='6' AND success", Integer.class));
        var key = UUID.randomUUID();
        var address = Address.builder().firstName("Test").lastName("Owner").streetAddress("123 Test Street")
                .city("Hanoi").state("Hanoi").postalCode("100000").country("VN").build();
        var order = orders.saveAndFlush(Order.builder().checkoutKey(key.toString())
                .orderNumber("ORD-" + key.toString().substring(0, 8)).userId("owner")
                .status(OrderStatus.PENDING).orderType(OrderType.STANDARD)
                .totalAmount(Money.of(1.00, "USD")).subtotalAmount(Money.of(1.00, "USD"))
                .shippingAddress(address).build());
        long id = order.getId();
        var operation = UUID.randomUUID();
        var line = UUID.randomUUID();
        try {
            jdbc.update("UPDATE orders SET allocation_phase='CLOSING', checkout_close_operation=? WHERE id=?", operation, id);
            jdbc.update("INSERT INTO inventory_allocation_intents(operation_id,order_id,target_kind,target_id,quantity,owner_id,state) VALUES (?,?,'PRODUCT',1,1,'owner','RESERVED')", line, id);
            var payment = mock(PaymentClosureClient.class);
            var inventory = mock(InventoryReservationClient.class);
            when(payment.close(eq(id), any())).thenReturn(new PaymentClosureClient.Result(id, operation, "SAFE", null));
            when(inventory.release(line)).thenReturn(new InventoryReservationClient.Reservation(line, "PRODUCT", 1L, 1, "owner", "RELEASED", null));
            new CheckoutExpiryRecovery(jdbc, tx, orders, payment, inventory).process(id);
            var row = jdbc.queryForMap("SELECT allocation_phase,status,cancelled_at,cancellation_reason FROM orders WHERE id=?", id);
            assertEquals("CLOSED", row.get("allocation_phase"));
            assertEquals("CANCELLED", row.get("status"));
            assertNotNull(row.get("cancelled_at"));
            assertEquals("Checkout expired", row.get("cancellation_reason"));
        } finally {
            jdbc.update("DELETE FROM inventory_allocation_intents WHERE order_id=?", id);
            orders.deleteById(id);
        }
    }
}
