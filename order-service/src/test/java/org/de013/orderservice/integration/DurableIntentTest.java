package org.de013.orderservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.de013.common.dto.ApiResult;
import org.de013.orderservice.client.CartClient;
import org.de013.orderservice.dto.CartDto;
import org.de013.orderservice.dto.request.CreateOrderRequest;
import org.de013.orderservice.entity.enums.OrderType;
import org.de013.orderservice.entity.valueobject.Address;
import org.de013.orderservice.service.impl.CheckoutOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@SpringBootTest(properties = {"spring.cloud.config.enabled=false", "spring.cloud.bus.enabled=false", "eureka.client.enabled=false", "spring.jpa.hibernate.ddl-auto=validate", "order.allocation.recovery-delay-ms=3600000"})
class DurableIntentTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        org.flywaydb.core.Flyway.configure().dataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()).target("3").load().migrate();
        try (var connection = java.sql.DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO checkout_attempts(attempt_key,user_id) VALUES ('legacy-key','owner')");
            statement.executeUpdate("INSERT INTO orders(order_number,user_id,status,order_type,created_at,updated_at,checkout_key) VALUES ('LEGACY','owner','PENDING','STANDARD',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'legacy-key')");
        } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
        r.add("spring.datasource.url", DB::getJdbcUrl); r.add("spring.datasource.username", DB::getUsername); r.add("spring.datasource.password", DB::getPassword);
    }
    @Autowired CheckoutOrderService checkout;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired org.de013.orderservice.service.OrderService orderService;
    @MockBean org.de013.orderservice.client.ProductCatalogClient catalog;
    @MockBean CartClient carts;
    @MockBean org.de013.orderservice.client.InventoryReservationClient reservations;
    @Autowired org.de013.orderservice.service.impl.AllocationRecovery recovery;
    @Autowired org.de013.orderservice.service.impl.PaymentEventProcessor paymentEvents;

    private long paymentOrder(String status, String phase) {
        return jdbc.queryForObject("INSERT INTO orders(order_number,user_id,status,order_type,created_at,updated_at,total_amount,currency,allocation_phase,shipping_first_name,shipping_last_name,shipping_street_address,shipping_city,shipping_state,shipping_postal_code,shipping_country) VALUES (?, 'owner', ?, 'STANDARD', CURRENT_TIMESTAMP,CURRENT_TIMESTAMP, 12.50, 'USD', ?, 'Test','Owner','Street','City','State','12345','US') RETURNING id", Long.class, UUID.randomUUID().toString(), status, phase);
    }

    private String paymentEvent(UUID event, long order, long payment) {
        return "{\"eventId\":\"" + event + "\",\"eventType\":\"PaymentSucceeded\",\"schemaVersion\":1,\"occurredAt\":\"2026-01-01T00:00:00Z\",\"paymentId\":" + payment + ",\"paymentNumber\":\"P-" + payment + "\",\"orderId\":" + order + ",\"provider\":\"STRIPE\",\"amount\":12.50,\"currency\":\"USD\"}";
    }

    @Test void paymentInboxConcurrentDuplicateAndConflictingPayload() throws Exception {
        long id = paymentOrder("PENDING", "READY"); var event = UUID.randomUUID(); var raw = paymentEvent(event, id, 101);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> paymentEvents.process(raw, Long.toString(id), "test-a", 0, 1));
            var b = pool.submit(() -> paymentEvents.process(raw, Long.toString(id), "test-a", 0, 2));
            a.get(); b.get();
        } finally { pool.shutdownNow(); }
        assertEquals("PAID", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id));
        assertEquals("FULFILL_PENDING", jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?", String.class, id));
        assertEquals("ACCEPTED", jdbc.queryForObject("SELECT result FROM payment_event_inbox WHERE event_id=?", String.class, event));
        paymentEvents.process(paymentEvent(event, id, 102), Long.toString(id), "test-a", 0, 3);
        assertEquals("EVENT_ID_CONFLICT", jdbc.queryForObject("SELECT reason FROM payment_event_quarantine WHERE topic='test-a' AND record_offset=3", String.class));
        assertEquals("ACCEPTED", jdbc.queryForObject("SELECT result FROM payment_event_inbox WHERE event_id=?", String.class, event));
        paymentEvents.process(paymentEvent(UUID.randomUUID(), id, 102), Long.toString(id), "test-a", 0, 4);
        assertEquals("PAYMENT_RECONCILIATION", jdbc.queryForObject("SELECT reason FROM payment_event_quarantine WHERE topic='test-a' AND record_offset=4", String.class));
        jdbc.update("UPDATE orders SET status='SHIPPED',allocation_phase='FULFILLED' WHERE id=?", id);
        paymentEvents.process(paymentEvent(UUID.randomUUID(), id, 101), Long.toString(id), "test-a", 0, 5);
        assertEquals("FULFILLED", jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?", String.class, id));
    }

    @Test void paymentInboxQuarantinesInvalidAndLegacyEvents() {
        long legacy = paymentOrder("PAID", null), pending = paymentOrder("PENDING", "READY");
        String raw = paymentEvent(UUID.randomUUID(), pending, 1);
        String[] values = {"{", raw.replace("\"schemaVersion\":1", "\"schemaVersion\":2"), raw, raw.replace("12.50", "13.00"), paymentEvent(UUID.randomUUID(), 999999999, 1), paymentEvent(UUID.randomUUID(), legacy, 1)};
        String[] reasons = {"INVALID_EVENT", "INVALID_EVENT", "KEY_MISMATCH", "AMOUNT_CURRENCY_MISMATCH", "UNKNOWN_ORDER", "PAYMENT_RECONCILIATION"};
        for (int i = 0; i < values.length; i++) {
            String key = i == 2 ? "wrong" : i == 4 ? "999999999" : i == 5 ? Long.toString(legacy) : Long.toString(pending);
            paymentEvents.process(values[i], key, "test-b", 0, i);
            assertEquals(reasons[i], jdbc.queryForObject("SELECT reason FROM payment_event_quarantine WHERE topic='test-b' AND record_offset=?", String.class, i));
        }
    }

    @Test void paymentInboxRollsBackPaidWhenInboxUpdateFails() {
        long id = paymentOrder("PENDING", "READY"); var event = UUID.randomUUID(); var raw = paymentEvent(event, id, 5);
        jdbc.execute("CREATE FUNCTION fail_payment_inbox_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected failure'; END $$");
        jdbc.execute("CREATE TRIGGER fail_payment_inbox_test BEFORE UPDATE ON payment_event_inbox FOR EACH ROW EXECUTE FUNCTION fail_payment_inbox_test()");
        try { assertThrows(Exception.class, () -> paymentEvents.process(raw, Long.toString(id), "test-c", 0, 1)); }
        finally { jdbc.execute("DROP TRIGGER fail_payment_inbox_test ON payment_event_inbox"); jdbc.execute("DROP FUNCTION fail_payment_inbox_test()"); }
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id));
        assertEquals("READY", jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?", String.class, id));
        assertNull(jdbc.queryForObject("SELECT accepted_payment_id FROM orders WHERE id=?", Long.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM payment_event_inbox WHERE event_id=?", Integer.class, event));
        paymentEvents.process(raw, Long.toString(id), "test-c", 0, 1);
        assertEquals("PAID", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id));
    }

    @Test void paymentInboxQuarantinesAllocationPhasesAndCorruptFinancials() {
        int offset = 0;
        for (String phase : new String[]{"ALLOCATING", "COMPENSATING", "FAILED", "RECONCILIATION"}) {
            long id = paymentOrder("PENDING", phase);
            paymentEvents.process(paymentEvent(UUID.randomUUID(), id, 11), Long.toString(id), "test-phases", 2, offset);
            assertEquals("ALLOCATION_RECONCILIATION", jdbc.queryForObject("SELECT reason FROM payment_event_quarantine WHERE topic='test-phases' AND record_offset=?", String.class, offset++));
            assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id));
            assertEquals(phase, jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?", String.class, id));
        }
        for (String column : new String[]{"total_amount", "currency"}) {
            long id = paymentOrder("PENDING", "READY");
            jdbc.update("UPDATE orders SET " + column + "=NULL WHERE id=?", id);
            paymentEvents.process(paymentEvent(UUID.randomUUID(), id, 11), Long.toString(id), "test-phases", 2, offset);
            assertEquals("AMOUNT_CURRENCY_MISMATCH", jdbc.queryForObject("SELECT reason FROM payment_event_quarantine WHERE topic='test-phases' AND record_offset=?", String.class, offset++));
        }
    }

    @Test void paymentInboxInvalidPayloadsAreDurableAndBounded() throws Exception {
        long id = paymentOrder("PENDING", "READY"); String raw = paymentEvent(UUID.randomUUID(), id, 7);
        String[] values = {"{", null, raw.replace("\"paymentNumber\":\"P-7\",", ""), raw.replace("\"paymentId\":7", "\"paymentId\":\"7\""),
                raw.replace("\"schemaVersion\":1", "\"schemaVersion\":2"), raw.replace("\"paymentId\":7", "\"paymentId\":9223372036854775808"),
                raw.replace("12.50", "0"), raw.replace("12.50", "-1"), raw.replace("\"USD\"", "\"EUR\""), paymentEvent(UUID.randomUUID(), id, 7).replace("12.50", "13"), raw};
        for (int i = 0; i < values.length; i++) {
            paymentEvents.process(values[i], i == 10 ? "wrong" : Long.toString(id), "test-invalid", 3, i);
            var row = jdbc.queryForMap("SELECT reason,raw_payload,payload_length,partition_id,record_offset FROM payment_event_quarantine WHERE topic='test-invalid' AND record_offset=?", i);
            assertEquals(i >= 8 && i <= 9 ? "AMOUNT_CURRENCY_MISMATCH" : i == 10 ? "KEY_MISMATCH" : "INVALID_EVENT", row.get("reason"));
            assertEquals(values[i] == null ? "" : values[i], row.get("raw_payload"));
            assertEquals(values[i] == null ? 0 : values[i].length(), row.get("payload_length"));
            assertEquals(3, row.get("partition_id")); assertEquals((long) i, row.get("record_offset"));
        }
        String large = "x".repeat(4095) + "😀" + "x".repeat(100);
        paymentEvents.process(large, "1", "test-invalid", 3, 11);
        var row = jdbc.queryForMap("SELECT raw_payload,payload_length,payload_hash,truncated FROM payment_event_quarantine WHERE topic='test-invalid' AND record_offset=11");
        assertEquals("x".repeat(4095), row.get("raw_payload")); assertEquals(large.length(), row.get("payload_length"));
        assertEquals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(large.getBytes(java.nio.charset.StandardCharsets.UTF_8))), row.get("payload_hash"));
        assertEquals(true, row.get("truncated"));
    }

    @Test void paymentInboxQuarantineDatabaseFailurePropagatesAndCanRetry() {
        jdbc.execute("CREATE FUNCTION fail_payment_quarantine_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected failure'; END $$");
        jdbc.execute("CREATE TRIGGER fail_payment_quarantine_test BEFORE INSERT ON payment_event_quarantine FOR EACH ROW EXECUTE FUNCTION fail_payment_quarantine_test()");
        try { assertThrows(org.springframework.dao.DataAccessException.class, () -> paymentEvents.process("{", "1", "test-db-failure", 1, 1)); }
        finally { jdbc.execute("DROP TRIGGER fail_payment_quarantine_test ON payment_event_quarantine"); jdbc.execute("DROP FUNCTION fail_payment_quarantine_test()"); }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM payment_event_quarantine WHERE topic='test-db-failure'", Integer.class));
        paymentEvents.process("{", "1", "test-db-failure", 1, 1);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM payment_event_quarantine WHERE topic='test-db-failure'", Integer.class));
    }

    @Test void paymentInboxQuarantinesNulInRawAndPaymentNumber() {
        long id = paymentOrder("PENDING", "READY");
        String[] values = {"bad\0record", paymentEvent(UUID.randomUUID(), id, 11).replace("P-11", "P-\\u000011")};
        for (int i = 0; i < values.length; i++) {
            paymentEvents.process(values[i], Long.toString(id), "test-nul", 0, i);
            var row = jdbc.queryForMap("SELECT reason,raw_payload,payload_length,payload_hash,truncated FROM payment_event_quarantine WHERE topic='test-nul' AND record_offset=?", i);
            assertEquals("INVALID_EVENT", row.get("reason"));
            assertEquals(values[i].replace("\0", "\\u0000"), row.get("raw_payload"));
            assertEquals(values[i].length(), row.get("payload_length"));
            assertEquals(false, row.get("truncated"));
        }
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id));
    }

    @Test void paymentInboxCompletedReplayAndMigrationV5() {
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='5' AND success", Integer.class));
        long id = paymentOrder("PENDING", "READY");
        paymentEvents.process(paymentEvent(UUID.randomUUID(), id, 45), Long.toString(id), "test-completed", 0, 1);
        jdbc.update("UPDATE orders SET status='COMPLETED', allocation_phase='FULFILLED' WHERE id=?", id);
        UUID replay = UUID.randomUUID();
        paymentEvents.process(paymentEvent(replay, id, 45), Long.toString(id), "test-completed", 0, 2);
        assertEquals("ACCEPTED", jdbc.queryForObject("SELECT result FROM payment_event_inbox WHERE event_id=?", String.class, replay));
        assertEquals("FULFILLED", jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?", String.class, id));
        assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id));
    }

    @Test void persistsSnapshotAndVariantIntentAndReplaysWithoutCartRead() throws Exception {
        var cart = mapper.readValue("""
            {"cart_id":1,"user_id":"owner","currency":"USD","items":[{"product_id":"17","variant_id":"22","product_name":"Item","product_sku":"SKU17","quantity":2,"unit_price":4,"currency":"USD"}]}
            """, CartDto.class);
        when(carts.getCart(1L)).thenReturn(ApiResult.success(cart));
        var address = Address.builder().firstName("Test").lastName("Owner").streetAddress("123 Test Street").city("Hanoi").state("Hanoi").postalCode("100000").country("VN").build();
        var request = new CreateOrderRequest(null, 1L, OrderType.STANDARD, address, address, "CARD", "USD", null);
        var key = UUID.randomUUID().toString();
        assertEquals("UNKNOWN", checkout.create(key, "owner", request).state());
        var operation = jdbc.queryForMap("SELECT i.operation_id,i.target_kind,i.target_id,i.quantity,i.owner_id FROM inventory_allocation_intents i JOIN orders o ON o.id=i.order_id WHERE o.checkout_key=?", key);
        assertEquals("VARIANT", operation.get("target_kind")); assertEquals(22L, operation.get("target_id"));
        assertEquals(2, operation.get("quantity")); assertEquals("owner", operation.get("owner_id"));
        assertEquals("UNKNOWN", checkout.create(key, "owner", request).state());
        assertEquals(operation.get("operation_id"), jdbc.queryForObject("SELECT i.operation_id FROM inventory_allocation_intents i JOIN orders o ON o.id=i.order_id WHERE o.checkout_key=?", UUID.class, key));
        verify(carts, times(1)).getCart(1L);
        assertEquals("ALLOCATING", jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE checkout_key=?", String.class, key));
    }
    private CreateOrderRequest request() {
        var address = Address.builder().firstName("Test").lastName("Owner").streetAddress("123 Test Street").city("Hanoi").state("Hanoi").postalCode("100000").country("VN").build();
        return new CreateOrderRequest(null, 1L, OrderType.STANDARD, address, address, "CARD", "USD", null);
    }

    private CartDto cart(String product, String variant, String owner) throws Exception {
        return mapper.readValue("{\"cart_id\":1,\"user_id\":\"" + owner + "\",\"currency\":\"USD\",\"items\":[{\"product_id\":" + product + ",\"variant_id\":" + variant + ",\"product_name\":\"Item\",\"product_sku\":\"SKU17\",\"quantity\":2,\"unit_price\":4,\"currency\":\"USD\"}]}", CartDto.class);
    }

    @Test void concurrentRequestsCommitOneAttemptAndStableIntent() throws Exception {
        var gate = new java.util.concurrent.CyclicBarrier(2);
        var cart = cart("\"17\"", "\"22\"", "owner");
        when(carts.getCart(1L)).thenAnswer(i -> { gate.await(); return ApiResult.success(cart); });
        var key = UUID.randomUUID().toString();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> checkout.create(key, "owner", request()));
            var b = pool.submit(() -> checkout.create(key, "owner", request()));
            assertEquals("UNKNOWN", a.get().state()); assertEquals("UNKNOWN", b.get().state());
            var id = jdbc.queryForObject("SELECT id FROM orders WHERE checkout_key=?", Long.class, key);
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM checkout_attempts WHERE attempt_key=?", Integer.class, key));
            var operation = jdbc.queryForObject("SELECT operation_id FROM inventory_allocation_intents WHERE order_id=?", UUID.class, id);
            assertNotNull(operation);
            assertEquals("UNKNOWN", checkout.create(key, "owner", request()).state());
            assertEquals(operation, jdbc.queryForObject("SELECT operation_id FROM inventory_allocation_intents WHERE order_id=?", UUID.class, id));
            assertEquals(false, orderService.getOrderById(id).inventoryReady());
            assertThrows(org.de013.common.exception.ConflictException.class, () -> orderService.markOrderAsPaid(id, 1L, "p"));
            verifyNoInteractions(catalog);
        } finally { pool.shutdownNow(); }
    }

    @Test void httpIdempotencyAndRecovery() throws Exception {
        when(carts.getCart(1L)).thenReturn(ApiResult.success(cart("\"17\"", "null", "owner")));
        String body = mapper.writeValueAsString(request());
        var post = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/orders").header("X-User-Id", "owner").header("X-User-Username", "owner").contentType("application/json").content(body);
        mvc.perform(post).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        for (var bad : new String[] {" ", "x".repeat(37)})
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/orders").header("X-User-Id", "owner").header("X-User-Username", "owner").header("Idempotency-Key",bad).contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        var key = "ordinary-" + UUID.randomUUID().toString().substring(0, 20);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/orders").header("X-User-Id", "owner").header("X-User-Username", "owner").header("Idempotency-Key", key).contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/orders/checkout/{key}", key).header("X-User-Id", "owner").header("X-User-Username", "owner")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.state").value("UNKNOWN"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/orders/checkout/{key}", key).header("X-User-Id", "other").header("X-User-Username", "other")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        var changed = new CreateOrderRequest(null, 1L, OrderType.STANDARD, request().shippingAddress(), request().billingAddress(), "CARD", "USD", "changed");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/orders").header("X-User-Id", "owner").header("X-User-Username", "owner").header("Idempotency-Key", key).contentType("application/json").content(mapper.writeValueAsString(changed))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        var hosted = UUID.randomUUID().toString();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/orders/checkout/{key}", hosted).header("X-User-Id", "owner").header("X-User-Username", "owner").contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted());
    }

    @Test void invalidCartFailsBeforeIntentAndWrongOwnerIsForbidden() throws Exception {
        for (var bad : new String[]{"null", "\"0\"", "\"not-a-number\""}) {
            when(carts.getCart(1L)).thenReturn(ApiResult.success(cart(bad, "null", "owner")));
            var key = UUID.randomUUID().toString();
            assertThrows(IllegalArgumentException.class, () -> checkout.create(key, "owner", request()));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM checkout_attempts WHERE attempt_key=?", Integer.class, key));
        }
        when(carts.getCart(1L)).thenReturn(ApiResult.success(cart("\"17\"", "\"0\"", "owner")));
        assertThrows(IllegalArgumentException.class, () -> checkout.create(UUID.randomUUID().toString(), "owner", request()));
        when(carts.getCart(1L)).thenReturn(ApiResult.success(cart("\"17\"", "null", "other")));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> checkout.create(UUID.randomUUID().toString(), "owner", request()));
    }

    @Test void intentFailureRollsBackAttemptAndOrder() throws Exception {
        var key = UUID.randomUUID().toString();
        var invalid = cart("\"17\"", "null", "owner").items().get(0);
        var broken = new org.de013.orderservice.dto.CartItemDto(invalid.itemId(), invalid.productId(), null,
                invalid.productName(), invalid.productDescription(), invalid.productImageUrl(), invalid.quantity(), invalid.unitPrice(),
                invalid.totalPrice(), invalid.currency(), invalid.specialInstructions(), invalid.isGift(), invalid.giftMessage(),
                invalid.addedAt(), invalid.categoryName(), invalid.productBrand(), invalid.discountAmount(), invalid.variantId());
        assertThrows(Exception.class, () -> orderService.createIntent(request().withUserId("owner"), key, "snapshot", java.util.List.of(broken)));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM checkout_attempts WHERE attempt_key=?", Integer.class, key));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM orders WHERE checkout_key=?", Integer.class, key));
    }

    @Test void httpRejectsInvalidCartAndUnauthorizedRecovery() throws Exception {
        String base = "{\"cart_id\":1,\"user_id\":\"owner\",\"currency\":\"USD\",\"items\":[{\"product_id\":\"17\",\"variant_id\":\"22\",\"product_name\":\"Item\",\"product_sku\":\"SKU17\",\"quantity\":2,\"unit_price\":4,\"currency\":\"USD\"}]}";
        String[] invalid = {base.replace("\"product_id\":\"17\"", "\"product_id\":null"), base.replace("\"product_id\":\"17\"", "\"product_id\":\"-1\""), base.replace("\"quantity\":2", "\"quantity\":null"), base.replace("\"quantity\":2", "\"quantity\":0"), base.replace("\"currency\":\"USD\"", "\"currency\":null"), base.replace("\"variant_id\":\"22\"", "\"variant_id\":\"invalid\""), base.replace("\"variant_id\":\"22\"", "\"variant_id\":\"0\"")};
        for (String json : invalid) {
            when(carts.getCart(1L)).thenReturn(ApiResult.success(mapper.readValue(json, CartDto.class)));
            var key = UUID.randomUUID().toString();
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/orders").header("X-User-Id", "owner").header("X-User-Username", "owner").header("Idempotency-Key",key).contentType("application/json").content(mapper.writeValueAsString(request()))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM orders WHERE checkout_key=?", Integer.class,key));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM checkout_attempts WHERE attempt_key=?", Integer.class,key));
        }
        when(carts.getCart(1L)).thenReturn(ApiResult.success(cart("\"17\"", "null", "other")));
        var key = UUID.randomUUID().toString();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/orders").header("X-User-Id", "owner").header("X-User-Username", "owner").header("Idempotency-Key",key).contentType("application/json").content(mapper.writeValueAsString(request()))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM orders WHERE checkout_key=?", Integer.class,key));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM checkout_attempts WHERE attempt_key=?", Integer.class,key));
        when(carts.getCart(1L)).thenReturn(ApiResult.success(cart("\"17\"", "null", "owner")));
        checkout.create(key, "owner", request());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/orders/checkout/{key}",key).header("X-User-Id","other").header("X-User-Username","other")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }

    @Test void separateConnectionObservesCommittedOrderAndIntent() throws Exception {
        when(carts.getCart(1L)).thenReturn(ApiResult.success(cart("\"17\"", "\"22\"", "owner")));
        String key = UUID.randomUUID().toString(); checkout.create(key, "owner", request());
        try (var connection = java.sql.DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()); var q = connection.prepareStatement("SELECT o.allocation_phase,i.quantity,i.operation_id FROM orders o JOIN inventory_allocation_intents i ON i.order_id=o.id WHERE o.checkout_key=?")) {
            q.setString(1,key); try (var rs=q.executeQuery()) { assertTrue(rs.next()); assertEquals("ALLOCATING",rs.getString(1)); assertEquals(2,rs.getInt(2)); assertNotNull(rs.getObject(3)); assertFalse(rs.next()); }
        }
    }

    @Test void changedCartReplayRetainsOriginalSnapshotAndOperation() throws Exception {
        when(carts.getCart(1L)).thenReturn(ApiResult.success(cart("\"17\"", "\"22\"", "owner")));
        String key = UUID.randomUUID().toString(); checkout.create(key,"owner",request());
        var sql = "SELECT i.operation_id,i.quantity,i.target_id,a.request_snapshot FROM inventory_allocation_intents i JOIN orders o ON o.id=i.order_id JOIN checkout_attempts a ON a.attempt_key=o.checkout_key WHERE o.checkout_key=?";
        var before = jdbc.queryForMap(sql,key);
        when(carts.getCart(1L)).thenReturn(ApiResult.success(cart("\"99\"", "\"88\"", "owner")));
        assertEquals("UNKNOWN",checkout.create(key,"owner",request()).state());
        assertEquals(before,jdbc.queryForMap(sql,key)); assertEquals(2,before.get("quantity")); assertEquals(22L,before.get("target_id")); verify(carts,times(1)).getCart(1L);
    }

    @Test void migratedLegacyAttemptRemainsReadableAndReplayFailsClosed() throws Exception {
        assertNull(jdbc.queryForObject("SELECT request_snapshot FROM checkout_attempts WHERE attempt_key='legacy-key'", String.class));
        Long id = jdbc.queryForObject("SELECT id FROM orders WHERE checkout_key='legacy-key'",Long.class);
        assertNull(orderService.getOrderById(id).inventoryReady());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/orders/checkout/legacy-key").header("X-User-Id","owner").header("X-User-Username","owner")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.state").value("READY"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/orders").header("X-User-Id","owner").header("X-User-Username","owner").header("Idempotency-Key","legacy-key").contentType("application/json").content(mapper.writeValueAsString(request()))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        verifyNoInteractions(carts);
    }

    @Test void reservedOrderBecomesPayableThenPaidRecordsFulfillment() throws Exception {
        when(carts.getCart(1L)).thenReturn(ApiResult.success(cart("\"17\"", "\"22\"", "owner")));
        var key = UUID.randomUUID().toString(); checkout.create(key,"owner",request());
        Long id = jdbc.queryForObject("SELECT id FROM orders WHERE checkout_key=?", Long.class,key);
        var operation = jdbc.queryForObject("SELECT operation_id FROM inventory_allocation_intents WHERE order_id=?",UUID.class,id);
        var reserved = new org.de013.orderservice.client.InventoryReservationClient.Reservation(operation,"VARIANT",22L,2,"owner","RESERVED",null);
        var fulfilled = new org.de013.orderservice.client.InventoryReservationClient.Reservation(operation,"VARIANT",22L,2,"owner","FULFILLED",null);
        when(reservations.lookup(operation)).thenReturn(reserved);
        recovery.process(id);
        assertEquals("READY",checkout.read(key,"owner").state()); assertEquals(true,orderService.getOrderById(id).inventoryReady());
        var deadline = jdbc.queryForObject("SELECT checkout_deadline FROM orders WHERE id=?", java.sql.Timestamp.class, id);
        assertNotNull(deadline);
        var now = java.time.LocalDateTime.now();
        assertTrue(deadline.toLocalDateTime().isAfter(now.plusMinutes(29)) && deadline.toLocalDateTime().isBefore(now.plusMinutes(31)));
        recovery.process(id);
        assertEquals(deadline, jdbc.queryForObject("SELECT checkout_deadline FROM orders WHERE id=?", java.sql.Timestamp.class, id));
        orderService.markOrderAsPaid(id,1L,"p");
        assertEquals("FULFILL_PENDING",jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?",String.class,id));
        when(reservations.fulfill(operation)).thenReturn(fulfilled);
        recovery.process(id);
        assertEquals("FULFILLED",jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?",String.class,id));
        verify(reservations).fulfill(operation);
    }

    @Test void staleReserveCannotOverwriteCompensatedStateAfterLostSessionLock() throws Exception {
        when(carts.getCart(1L)).thenReturn(ApiResult.success(cart("\"17\"", "\"22\"", "owner")));
        var key = UUID.randomUUID().toString(); checkout.create(key,"owner",request());
        Long id = jdbc.queryForObject("SELECT id FROM orders WHERE checkout_key=?",Long.class,key);
        UUID op = jdbc.queryForObject("SELECT operation_id FROM inventory_allocation_intents WHERE order_id=?",UUID.class,id);
        var entered = new java.util.concurrent.CountDownLatch(1); var resume = new java.util.concurrent.CountDownLatch(1);
        var reserved = new org.de013.orderservice.client.InventoryReservationClient.Reservation(op,"VARIANT",22L,2,"owner","RESERVED",null);
        when(reservations.lookup(op)).thenAnswer(call -> { entered.countDown(); assertTrue(resume.await(20,java.util.concurrent.TimeUnit.SECONDS)); return reserved; });
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var future = executor.submit(() -> recovery.process(id));
            assertTrue(entered.await(20,java.util.concurrent.TimeUnit.SECONDS));
            jdbc.update("UPDATE orders SET allocation_phase='COMPENSATING' WHERE id=?",id);
            jdbc.update("UPDATE inventory_allocation_intents SET state='RELEASED' WHERE order_id=?",id);
            jdbc.update("UPDATE orders SET allocation_phase='FAILED', status='FAILED' WHERE id=?",id);
            resume.countDown(); future.get(20,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals("FAILED",jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?",String.class,id));
            assertEquals("RELEASED",jdbc.queryForObject("SELECT state FROM inventory_allocation_intents WHERE order_id=?",String.class,id));
        } finally { resume.countDown(); executor.shutdownNow(); }
    }

    @Test void promotionReloadsDurableLineStates() throws Exception {
        when(carts.getCart(1L)).thenReturn(ApiResult.success(cart("\"17\"", "\"22\"", "owner")));
        var key = UUID.randomUUID().toString(); checkout.create(key,"owner",request());
        Long id = jdbc.queryForObject("SELECT id FROM orders WHERE checkout_key=?",Long.class,key);
        UUID op = jdbc.queryForObject("SELECT operation_id FROM inventory_allocation_intents WHERE order_id=?",UUID.class,id);
        jdbc.update("UPDATE inventory_allocation_intents SET state='RESERVED' WHERE order_id=?",id);
        jdbc.update("INSERT INTO inventory_allocation_intents(operation_id,order_id,target_kind,target_id,quantity,owner_id,state) VALUES (? ,?,'PRODUCT',17,1,'owner','PENDING')", UUID.randomUUID(),id);
        when(reservations.lookup(any())).thenThrow(new RuntimeException("catalog offline"));
        recovery.process(id);
        assertEquals("ALLOCATING",jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?",String.class,id));
    }

    @Test void lostAdvisorySessionSecondWorkerCompensatesBeforeStaleResponse() throws Exception {
        var item = cart("\"17\"", "\"22\"", "owner").items().get(0);
        var key = UUID.randomUUID().toString();
        orderService.createIntent(request().withUserId("owner"), key, "snapshot", java.util.List.of(item,item));
        Long id = jdbc.queryForObject("SELECT id FROM orders WHERE checkout_key=?",Long.class,key);
        var ids = jdbc.queryForList("SELECT operation_id FROM inventory_allocation_intents WHERE order_id=? ORDER BY operation_id",UUID.class,id);
        UUID first = ids.get(0), second = ids.get(1);
        var states = new java.util.concurrent.ConcurrentHashMap<UUID,String>();
        var entered = new java.util.concurrent.CountDownLatch(1); var resume = new java.util.concurrent.CountDownLatch(1);
        var response = (java.util.function.BiFunction<UUID,String,org.de013.orderservice.client.InventoryReservationClient.Reservation>) (op,state) -> new org.de013.orderservice.client.InventoryReservationClient.Reservation(op,"VARIANT",22L,2,"owner",state,null);
        var http = feign.Request.create(feign.Request.HttpMethod.GET,"http://catalog",java.util.Map.of(),null,java.nio.charset.StandardCharsets.UTF_8,null);
        when(reservations.lookup(any())).thenAnswer(call -> { UUID op=call.getArgument(0); String state=states.get(op); if(state==null) throw feign.FeignException.errorStatus("lookup",feign.Response.builder().request(http).status(404).reason("not found").build()); return response.apply(op,state); });
        when(reservations.reserve(any())).thenAnswer(call -> { org.de013.orderservice.client.InventoryReservationClient.Request r=call.getArgument(0); String value=states.computeIfAbsent(r.operationId(), op -> op.equals(first) ? "RESERVED" : "REJECTED"); if(r.operationId().equals(first)) { entered.countDown(); if(!resume.await(30,java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("reserve response timed out"); } return response.apply(r.operationId(),value); });
        when(reservations.release(any())).thenAnswer(call -> { UUID op=call.getArgument(0); assertEquals("RESERVED",states.replace(op,"RELEASED")); return response.apply(op,"RELEASED"); });
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var old = executor.submit(() -> recovery.process(id));
            assertTrue(entered.await(30,java.util.concurrent.TimeUnit.SECONDS));
            Integer pid = jdbc.queryForObject("SELECT pid FROM pg_locks WHERE locktype='advisory' AND granted AND classid=0 AND objid=?",Integer.class,id);
            assertNotNull(pid); assertEquals(true,jdbc.queryForObject("SELECT pg_terminate_backend(?)",Boolean.class,pid));
            recovery.process(id);
            recovery.process(id);
            assertEquals("FAILED",jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?",String.class,id));
            resume.countDown(); old.get(30,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals("FAILED",jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?",String.class,id));
            assertEquals("RELEASED",jdbc.queryForObject("SELECT state FROM inventory_allocation_intents WHERE operation_id=?",String.class,first));
            assertEquals("REJECTED",jdbc.queryForObject("SELECT state FROM inventory_allocation_intents WHERE operation_id=?",String.class,second));
            assertFalse(states.containsValue("RESERVED")); assertEquals(false,orderService.getOrderById(id).inventoryReady());
        } finally { resume.countDown(); executor.shutdownNow(); }
    }

    @Test void concurrentWorkersDoNotDuplicateEffectiveReservation() throws Exception {
        var item = cart("\"17\"", "\"22\"", "owner").items().get(0);
        var key = UUID.randomUUID().toString(); orderService.createIntent(request().withUserId("owner"),key,"snapshot",java.util.List.of(item));
        Long id = jdbc.queryForObject("SELECT id FROM orders WHERE checkout_key=?",Long.class,key);
        UUID op = jdbc.queryForObject("SELECT operation_id FROM inventory_allocation_intents WHERE order_id=?",UUID.class,id);
        var entered = new java.util.concurrent.CountDownLatch(1); var resume = new java.util.concurrent.CountDownLatch(1);
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var response = new org.de013.orderservice.client.InventoryReservationClient.Reservation(op,"VARIANT",22L,2,"owner","RESERVED",null);
        var http = feign.Request.create(feign.Request.HttpMethod.GET,"http://catalog",java.util.Map.of(),null,java.nio.charset.StandardCharsets.UTF_8,null);
        when(reservations.lookup(op)).thenAnswer(call -> { throw feign.FeignException.errorStatus("lookup",feign.Response.builder().request(http).status(404).reason("not found").build()); });
        when(reservations.reserve(any())).thenAnswer(call -> { count.incrementAndGet(); entered.countDown(); if(!resume.await(30,java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("timeout"); return response; });
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var worker=executor.submit(() -> recovery.process(id)); assertTrue(entered.await(30,java.util.concurrent.TimeUnit.SECONDS));
            recovery.process(id); assertEquals(1,count.get()); resume.countDown(); worker.get(30,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals("READY",jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?",String.class,id)); assertEquals(1,count.get());
        } finally { resume.countDown(); executor.shutdownNow(); }
    }

    @Autowired javax.sql.DataSource dataSource;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    @Autowired org.de013.orderservice.repository.OrderRepository orderRepository;

    private org.de013.orderservice.service.impl.AllocationRecovery restartedProcessor() {
        return new org.de013.orderservice.service.impl.AllocationRecovery(dataSource,jdbc,transactionTemplate,orderRepository,reservations);
    }

    @Test void lostReserveAndFulfillResponsesRecoverFromPersistedIntents() throws Exception {
        var item = cart("\"17\"", "\"22\"", "owner").items().get(0);
        var key = UUID.randomUUID().toString(); orderService.createIntent(request().withUserId("owner"),key,"snapshot",java.util.List.of(item));
        Long id=jdbc.queryForObject("SELECT id FROM orders WHERE checkout_key=?",Long.class,key);
        UUID op=jdbc.queryForObject("SELECT operation_id FROM inventory_allocation_intents WHERE order_id=?",UUID.class,id);
        var state=new java.util.concurrent.atomic.AtomicReference<String>(); var reserveEffects=new java.util.concurrent.atomic.AtomicInteger(); var fulfillEffects=new java.util.concurrent.atomic.AtomicInteger();
        var http=feign.Request.create(feign.Request.HttpMethod.GET,"http://catalog",java.util.Map.of(),null,java.nio.charset.StandardCharsets.UTF_8,null);
        when(reservations.lookup(op)).thenAnswer(c -> { if(state.get()==null) throw feign.FeignException.errorStatus("lookup",feign.Response.builder().request(http).status(404).reason("missing").build()); return new org.de013.orderservice.client.InventoryReservationClient.Reservation(op,"VARIANT",22L,2,"owner",state.get(),null); });
        when(reservations.reserve(any())).thenAnswer(c -> { assertTrue(state.compareAndSet(null,"RESERVED")); reserveEffects.incrementAndGet(); throw new RuntimeException("lost reserve response"); });
        recovery.process(id);
        assertEquals("ALLOCATING",jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?",String.class,id));
        restartedProcessor().process(id);
        assertEquals("READY",jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?",String.class,id)); assertEquals(1,reserveEffects.get());
        orderService.markOrderAsPaid(id,1L,"p");
        assertEquals("FULFILL_PENDING",jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?",String.class,id));
        when(reservations.fulfill(op)).thenAnswer(c -> { assertTrue(state.compareAndSet("RESERVED","FULFILLED")); fulfillEffects.incrementAndGet(); throw new RuntimeException("lost fulfill response"); });
        recovery.process(id); restartedProcessor().process(id);
        assertEquals("FULFILLED",jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?",String.class,id)); assertEquals(1,fulfillEffects.get());
        verify(reservations,times(1)).reserve(argThat(r -> r.operationId().equals(op))); verify(reservations,times(1)).fulfill(op);
    }

    @Test void rejectedSecondLineAndLostReleaseRecoverAfterRestart() throws Exception {
        var item=cart("\"17\"","\"22\"","owner").items().get(0); var key=UUID.randomUUID().toString();
        orderService.createIntent(request().withUserId("owner"),key,"snapshot",java.util.List.of(item,item));
        Long id=jdbc.queryForObject("SELECT id FROM orders WHERE checkout_key=?",Long.class,key);
        var ids=jdbc.queryForList("SELECT operation_id FROM inventory_allocation_intents WHERE order_id=? ORDER BY operation_id",UUID.class,id);
        var states=new java.util.concurrent.ConcurrentHashMap<UUID,String>(); var releases=new java.util.concurrent.atomic.AtomicInteger();
        var http=feign.Request.create(feign.Request.HttpMethod.GET,"http://catalog",java.util.Map.of(),null,java.nio.charset.StandardCharsets.UTF_8,null);
        java.util.function.Function<UUID,org.de013.orderservice.client.InventoryReservationClient.Reservation> response=op -> new org.de013.orderservice.client.InventoryReservationClient.Reservation(op,"VARIANT",22L,2,"owner",states.get(op),null);
        when(reservations.lookup(any())).thenAnswer(c -> { UUID op=c.getArgument(0); if(!states.containsKey(op)) throw feign.FeignException.errorStatus("lookup",feign.Response.builder().request(http).status(404).reason("missing").build()); return response.apply(op); });
        when(reservations.reserve(any())).thenAnswer(c -> { org.de013.orderservice.client.InventoryReservationClient.Request r=c.getArgument(0); states.putIfAbsent(r.operationId(),r.operationId().equals(ids.get(0)) ? "RESERVED" : "REJECTED"); return response.apply(r.operationId()); });
        when(reservations.release(any())).thenAnswer(c -> { UUID op=c.getArgument(0); assertTrue(states.replace(op,"RESERVED","RELEASED")); releases.incrementAndGet(); throw new RuntimeException("lost release response"); });
        recovery.process(id); assertEquals("COMPENSATING",jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?",String.class,id));
        recovery.process(id); restartedProcessor().process(id);
        assertEquals("FAILED",jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?",String.class,id)); assertFalse(states.containsValue("RESERVED")); assertEquals(1,releases.get());
    }

    @Test void ordinaryAndHostedDriveCommittedIntentBeforeHttpResponse() throws Exception {
        when(carts.getCart(1L)).thenReturn(ApiResult.success(cart("\"17\"","\"22\"","owner")));
        var http=feign.Request.create(feign.Request.HttpMethod.GET,"http://catalog",java.util.Map.of(),null,java.nio.charset.StandardCharsets.UTF_8,null);
        when(reservations.lookup(any())).thenAnswer(c -> { throw feign.FeignException.errorStatus("lookup",feign.Response.builder().request(http).status(404).reason("missing").build()); });
        when(reservations.reserve(any())).thenAnswer(c -> { org.de013.orderservice.client.InventoryReservationClient.Request r=c.getArgument(0); assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM inventory_allocation_intents WHERE operation_id=?",Integer.class,r.operationId())); return new org.de013.orderservice.client.InventoryReservationClient.Reservation(r.operationId(),r.targetKind(),r.targetId(),r.quantity(),r.owner(),"RESERVED",null); });
        String body=mapper.writeValueAsString(request());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/orders").header("X-User-Id","owner").header("X-User-Username","owner").header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.inventoryReady").value(true));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/orders/checkout/{key}",UUID.randomUUID()).header("X-User-Id","owner").header("X-User-Username","owner").contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.state").value("READY"));
    }

    @Test void paidCommitsWhileCatalogDownAndRestartFulfillsOnce() throws Exception {
        var item=cart("\"17\"","\"22\"","owner").items().get(0); String key=UUID.randomUUID().toString();
        orderService.createIntent(request().withUserId("owner"),key,"snapshot",java.util.List.of(item));
        Long id=jdbc.queryForObject("SELECT id FROM orders WHERE checkout_key=?",Long.class,key);
        UUID op=jdbc.queryForObject("SELECT operation_id FROM inventory_allocation_intents WHERE order_id=?",UUID.class,id);
        var reserved=new org.de013.orderservice.client.InventoryReservationClient.Reservation(op,"VARIANT",22L,2,"owner","RESERVED",null);
        when(reservations.lookup(op)).thenReturn(reserved); recovery.process(id);
        when(reservations.lookup(op)).thenThrow(new RuntimeException("catalog down"));
        clearInvocations(reservations);
        orderService.markOrderAsPaid(id,1L,"confirmed"); verifyNoInteractions(reservations);
        try (var c=java.sql.DriverManager.getConnection(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()); var q=c.prepareStatement("SELECT status,allocation_phase FROM orders WHERE id=?")) {
            q.setLong(1,id); try(var rs=q.executeQuery()) { assertTrue(rs.next()); assertEquals("PAID",rs.getString(1)); assertEquals("FULFILL_PENDING",rs.getString(2)); }
        }
        assertEquals(true,orderService.getOrderById(id).inventoryReady()); assertEquals("READY",checkout.read(key,"owner").state());
        assertThrows(org.de013.common.exception.ConflictException.class,() -> orderService.cancelOrder(id,"cancel"));
        assertThrows(org.de013.common.exception.ConflictException.class,() -> orderService.markOrderPaymentFailed(id,"failed"));
        doReturn(reserved).when(reservations).lookup(op);
        when(reservations.fulfill(op)).thenReturn(new org.de013.orderservice.client.InventoryReservationClient.Reservation(op,"VARIANT",22L,2,"owner","FULFILLED",null));
        restartedProcessor().process(id); restartedProcessor().process(id);
        assertEquals("FULFILLED",jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?",String.class,id)); assertEquals("READY",checkout.read(key,"owner").state()); verify(reservations,times(1)).fulfill(op);
    }

    @Test void httpTimeoutAndRejectedAllocationExposeRecoverableOutcomes() throws Exception {
        when(carts.getCart(1L)).thenReturn(ApiResult.success(cart("\"17\"","null","owner")));
        String body=mapper.writeValueAsString(request()); String key=UUID.randomUUID().toString();
        when(reservations.lookup(any())).thenThrow(new RuntimeException("timeout"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/orders").header("X-User-Id","owner").header("X-User-Username","owner").header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/orders/checkout/{key}",key).header("X-User-Id","owner").header("X-User-Username","owner")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.state").value("UNKNOWN"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/orders/checkout/{key}",UUID.randomUUID()).header("X-User-Id","owner").header("X-User-Username","owner").contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.state").value("UNKNOWN"));
        var http=feign.Request.create(feign.Request.HttpMethod.GET,"http://catalog",java.util.Map.of(),null,java.nio.charset.StandardCharsets.UTF_8,null);
        doAnswer(c -> { throw feign.FeignException.errorStatus("lookup",feign.Response.builder().request(http).status(404).reason("missing").build()); }).when(reservations).lookup(any());
        when(reservations.reserve(any())).thenAnswer(c -> { org.de013.orderservice.client.InventoryReservationClient.Request r=c.getArgument(0); return new org.de013.orderservice.client.InventoryReservationClient.Reservation(r.operationId(),r.targetKind(),r.targetId(),r.quantity(),r.owner(),"REJECTED","INSUFFICIENT_STOCK"); });
        key=UUID.randomUUID().toString();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/orders").header("X-User-Id","owner").header("X-User-Username","owner").header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/orders/checkout/{key}",key).header("X-User-Id","owner").header("X-User-Username","owner")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.state").value("FAILED"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/orders/checkout/{key}",UUID.randomUUID()).header("X-User-Id","owner").header("X-User-Username","owner").contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.state").value("FAILED"));
    }

    @Test void delayedReserveAfter404IsFencedBeforeCompensation() throws Exception {
        var item=cart("\"17\"","\"22\"","owner").items().get(0); var key=UUID.randomUUID().toString();
        orderService.createIntent(request().withUserId("owner"),key,"snapshot",java.util.List.of(item,item));
        Long id=jdbc.queryForObject("SELECT id FROM orders WHERE checkout_key=?",Long.class,key);
        var ids=jdbc.queryForList("SELECT operation_id FROM inventory_allocation_intents WHERE order_id=? ORDER BY operation_id",UUID.class,id); UUID first=ids.get(0),second=ids.get(1);
        var states=new java.util.concurrent.ConcurrentHashMap<UUID,String>(); var gate=new Object();
        var claimed=new java.util.concurrent.CountDownLatch(1); var commit=new java.util.concurrent.CountDownLatch(1); var replay=new java.util.concurrent.CountDownLatch(1); var returnOriginal=new java.util.concurrent.CountDownLatch(1);
        var calls=new java.util.concurrent.atomic.AtomicInteger(); var released=new java.util.concurrent.atomic.AtomicInteger();
        var http=feign.Request.create(feign.Request.HttpMethod.GET,"http://catalog",java.util.Map.of(),null,java.nio.charset.StandardCharsets.UTF_8,null);
        java.util.function.Function<UUID,org.de013.orderservice.client.InventoryReservationClient.Reservation> response=op -> new org.de013.orderservice.client.InventoryReservationClient.Reservation(op,"VARIANT",22L,2,"owner",states.get(op),null);
        when(reservations.lookup(any())).thenAnswer(c -> { UUID op=c.getArgument(0); if(!states.containsKey(op)) throw feign.FeignException.errorStatus("lookup",feign.Response.builder().request(http).status(404).reason("missing").build()); return response.apply(op); });
        when(reservations.reserve(any())).thenAnswer(c -> {
            org.de013.orderservice.client.InventoryReservationClient.Request r=c.getArgument(0); UUID op=r.operationId();
            if(op.equals(second)) { states.putIfAbsent(op,"REJECTED"); return response.apply(op); }
            int n=calls.incrementAndGet();
            if(n==1) {
                synchronized(gate) { claimed.countDown(); if(!commit.await(30,java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("commit timeout"); states.put(op,"RESERVED"); }
                if(!returnOriginal.await(30,java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("response timeout");
                return new org.de013.orderservice.client.InventoryReservationClient.Reservation(op,"VARIANT",22L,2,"owner","RESERVED",null);
            }
            replay.countDown(); synchronized(gate) {
                String state=states.get(op); if("RELEASED".equals(state)) throw feign.FeignException.errorStatus("reserve",feign.Response.builder().request(http).status(409).reason("conflict").build());
                return response.apply(op);
            }
        });
        when(reservations.release(first)).thenAnswer(c -> { assertTrue(states.replace(first,"RESERVED","RELEASED")); released.incrementAndGet(); return response.apply(first); });
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var original=pool.submit(() -> recovery.process(id)); assertTrue(claimed.await(30,java.util.concurrent.TimeUnit.SECONDS));
            Integer pid=jdbc.queryForObject("SELECT pid FROM pg_locks WHERE locktype='advisory' AND granted AND classid=0 AND objid=?",Integer.class,id);
            assertEquals(true,jdbc.queryForObject("SELECT pg_terminate_backend(?)",Boolean.class,pid));
            // A separate worker reaches the rejected line while the original claim is uncommitted.
            states.put(second,"REJECTED");
            var other=pool.submit(() -> recovery.process(id));
            assertTrue(replay.await(30,java.util.concurrent.TimeUnit.SECONDS)); commit.countDown(); other.get(30,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals("FAILED",jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?",String.class,id));
            returnOriginal.countDown(); original.get(30,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals("RELEASED",states.get(first)); assertEquals(1,released.get()); assertFalse(states.containsValue("RESERVED"));
            assertEquals("FAILED",jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?",String.class,id)); assertEquals(false,orderService.getOrderById(id).inventoryReady());
        } finally { commit.countDown(); returnOriginal.countDown(); pool.shutdownNow(); }
    }

    @Test void schedulerTraversesPastFiftyUnknownOrdersAndWrapsToEarlierWork() throws Exception {
        var item=cart("\"17\"","\"22\"","owner").items().get(0);
        var ids=new java.util.ArrayList<Long>();
        for(int i=0;i<51;i++) {
            String key=UUID.randomUUID().toString();
            orderService.createIntent(request().withUserId("owner"),key,"snapshot",java.util.List.of(item));
            ids.add(jdbc.queryForObject("SELECT id FROM orders WHERE checkout_key=?",Long.class,key));
        }
        Long later=ids.get(50); UUID op=jdbc.queryForObject("SELECT operation_id FROM inventory_allocation_intents WHERE order_id=?",UUID.class,later);
        jdbc.update("UPDATE orders SET status='PAID', allocation_phase='FULFILL_PENDING' WHERE id=?",later);
        jdbc.update("UPDATE inventory_allocation_intents SET state='RESERVED' WHERE order_id=?",later);
        var reserved=new org.de013.orderservice.client.InventoryReservationClient.Reservation(op,"VARIANT",22L,2,"owner","RESERVED",null);
        var fulfilled=new org.de013.orderservice.client.InventoryReservationClient.Reservation(op,"VARIANT",22L,2,"owner","FULFILLED",null);
        when(reservations.lookup(any())).thenAnswer(c -> { UUID queried=c.getArgument(0); if(queried.equals(op)) return reserved; throw new RuntimeException("catalog unavailable"); });
        when(reservations.fulfill(op)).thenReturn(fulfilled);
        var processor=restartedProcessor();
        // Earlier test data may also be eligible; every bounded page advances even on failure.
        for(int i=0;i<10 && !"FULFILLED".equals(jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?",String.class,later));i++) processor.recover();
        assertEquals("FULFILLED",jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?",String.class,later));
        verify(reservations,times(1)).fulfill(op);
        Long earlier=ids.get(0); UUID earlierOp=jdbc.queryForObject("SELECT operation_id FROM inventory_allocation_intents WHERE order_id=?",UUID.class,earlier);
        var earlierReserved=new org.de013.orderservice.client.InventoryReservationClient.Reservation(earlierOp,"VARIANT",22L,2,"owner","RESERVED",null);
        doReturn(earlierReserved).when(reservations).lookup(earlierOp);
        for(int i=0;i<10 && !"READY".equals(jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?",String.class,earlier));i++) processor.recover();
        assertEquals("READY",jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?",String.class,earlier));
    }

}
