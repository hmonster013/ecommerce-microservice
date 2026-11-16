package org.de013.paymentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.de013.paymentservice.dto.external.OrderDto;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.controller.PaymentController;
import org.de013.paymentservice.dto.stripe.StripePaymentResponse;
import org.de013.paymentservice.exception.PaymentExceptionHandler;
import org.de013.paymentservice.exception.PaymentProcessingException;
import org.de013.paymentservice.gateway.stripe.StripePaymentGateway;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.cloud.bus.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect", "spring.flyway.enabled=true",
        "payment.events.enabled=true", "payment.gateways.stripe.checkout-return-url=http://localhost:4200/checkout/result"})
@ActiveProfiles("test")
@org.testcontainers.junit.jupiter.Testcontainers
class PaymentOutboxStageATest {
    @org.testcontainers.junit.jupiter.Container static final org.testcontainers.containers.PostgreSQLContainer<?> DB = new org.testcontainers.containers.PostgreSQLContainer<>("postgres:17-alpine");
    @org.springframework.test.context.DynamicPropertySource static void database(org.springframework.test.context.DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", DB::getJdbcUrl);
        p.add("spring.datasource.username", DB::getUsername);
        p.add("spring.datasource.password", DB::getPassword);
        p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
    @org.springframework.beans.factory.annotation.Autowired HostedCheckoutStore hostedStore;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired org.de013.paymentservice.repository.PaymentRepository repository;
    @org.springframework.boot.test.mock.mockito.MockBean org.de013.paymentservice.client.HostedOrderClient orders;
    @org.springframework.boot.test.mock.mockito.MockBean org.de013.paymentservice.client.OrderServiceClient orderClient;
    @org.springframework.boot.test.mock.mockito.MockBean org.de013.paymentservice.client.NotificationServiceClient notifications;
    @org.springframework.boot.test.mock.mockito.MockBean org.de013.paymentservice.client.UserServiceClient users;
    @org.springframework.boot.test.mock.mockito.MockBean org.de013.paymentservice.gateway.PaymentGatewayFactory factory;
    @org.springframework.boot.test.mock.mockito.MockBean org.de013.paymentservice.gateway.stripe.StripePaymentGateway stripe;
    @org.springframework.boot.test.mock.mockito.MockBean org.de013.paymentservice.gateway.vnpay.VnpayPaymentGateway vnpayGateway;
    @org.springframework.boot.test.mock.mockito.MockBean org.de013.paymentservice.service.validation.PaymentRequestValidator validator;
    @org.springframework.beans.factory.annotation.Autowired PaymentService payments;
    @org.springframework.beans.factory.annotation.Autowired WebhookService webhook;
    @org.springframework.beans.factory.annotation.Autowired VnpayIpnService ipn;
    @org.springframework.beans.factory.annotation.Autowired org.de013.paymentservice.config.PaymentGatewayConfig gatewayConfig;
    @org.springframework.beans.factory.annotation.Autowired PaymentOrderGuard guard;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.transaction.PlatformTransactionManager tx;
    @org.springframework.beans.factory.annotation.Autowired jakarta.validation.Validator validatorBean;
    @org.springframework.beans.factory.annotation.Autowired PaymentRetryAttempts retryAttempts;
    @org.springframework.beans.factory.annotation.Autowired CheckoutClosureService closures;

    @Test void failedOwnerRetryConfirmsSameIntentOnceWithDurableKey() throws Exception {
        var p = payment(99373, "STRIPE", PaymentStatus.FAILED);
        p.setStripeIntentCreatedByService(true);
        p.setStripeIntentLivemode(false);
        repository.saveAndFlush(p);
        var order = new OrderDto();
        order.setId(p.getOrderId()); order.setUserId("owner"); order.setStatus("PENDING");
        order.setInventoryReady(true); order.setPaymentRetryEligible(true); order.setCurrency("USD");
        var total = new OrderDto.MoneyDto(); total.setAmount(p.getAmount()); total.setCurrency("USD"); order.setTotalAmount(total);
        when(orderClient.getOrderById(p.getOrderId())).thenReturn(ResponseEntity.ok(order));
        when(stripe.getSupportedCurrencies()).thenCallRealMethod();
        when(factory.getGateway("STRIPE")).thenReturn(new StripePaymentGateway(gatewayConfig, null, null, null));

        var posts = new AtomicInteger();
        var providerKey = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/payment_intents/pi_99373", exchange -> {
            boolean confirm = "POST".equals(exchange.getRequestMethod()) && exchange.getRequestURI().getPath().endsWith("/confirm");
            if (confirm) { posts.incrementAndGet(); providerKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key")); }
            String status = confirm ? "succeeded" : "requires_payment_method";
            byte[] body = ("{\"id\":\"pi_99373\",\"object\":\"payment_intent\",\"livemode\":false,\"currency\":\"usd\",\"amount\":2500,\"status\":\"" + status + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        String base = Stripe.getApiBase(), key = Stripe.apiKey;
        server.start();
        try {
            Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); Stripe.apiKey = "sk_test_fixture";
            var response = payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "retry-key");
            assertEquals(p.getId(), response.getId());
            assertEquals("pi_99373", response.getStripePaymentIntentId());
            assertEquals(PaymentStatus.SUCCEEDED, response.getStatus());
            assertEquals(1, repository.findByOrderId(p.getOrderId()).size());
            assertEquals(1, posts.get());
            assertNotNull(providerKey.get());
            assertEquals(jdbc.queryForObject("select provider_key::text from payment_retry_attempt where payment_id=? and idempotency_key=?", String.class, p.getId(), "retry-key"), providerKey.get());
            order.setPaymentRetryEligible(false);
            var replay = payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "retry-key");
            assertEquals(p.getId(), replay.getId());
            assertEquals("pi_99373", replay.getStripePaymentIntentId());
            assertEquals(PaymentStatus.SUCCEEDED, replay.getStatus());
            assertEquals(1, posts.get());
            var mismatch = assertThrows(ResponseStatusException.class,
                    () -> payments.confirmPaymentForOwner(p.getId(), "pm_other", "owner", "retry-key"));
            assertEquals(HttpStatus.CONFLICT, mismatch.getStatusCode());
            assertEquals(1, posts.get());
        } finally { Stripe.overrideApiBase(base); Stripe.apiKey = key; server.stop(0); }
    }

    @Test void failedRetryRequiresOwnerAndKeyBeforeProvider() {
        var p = retryPayment(99374);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> payments.confirmPaymentForOwner(p.getId(), "pm_retry", "other", "key")).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> payments.confirmPaymentForOwner(p.getId(), "pm_retry", " ", "key")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", null)).getStatusCode());
        assertNoRetryOrProvider(p.getId());
    }

    @Test void failedRetryRejectsIneligibleOrUnavailableOrderBeforeProvider() {
        var p = retryPayment(99375);
        var order = retryOrder(p);
        when(orderClient.getOrderById(p.getOrderId())).thenReturn(ResponseEntity.ok(order));
        for (Boolean eligible : new Boolean[]{false, null}) {
            order.setPaymentRetryEligible(eligible);
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                    () -> payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key")).getStatusCode());
            assertNoRetryOrProvider(p.getId());
        }
        when(orderClient.getOrderById(p.getOrderId())).thenThrow(new IllegalStateException("order unavailable"));
        assertThrows(RuntimeException.class, () -> payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key"));
        assertNoRetryOrProvider(p.getId());
    }

    @Test void hostedRelationRejectsRetryBeforeProvider() {
        var checkout = hostedStore.reserve(99376L, "owner", new BigDecimal("25.00"), "https://fixture.test/checkout/result");
        var p = repository.findById(checkout.getPaymentId()).orElseThrow();
        p.setStatus(PaymentStatus.FAILED); p.setStripePaymentIntentId("pi_99376");
        p.setStripeIntentCreatedByService(true); p.setStripeIntentLivemode(false); repository.saveAndFlush(p);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key")).getStatusCode());
        assertNoRetryOrProvider(p.getId());
        verify(orderClient, never()).getOrderById(p.getOrderId());
    }

    @Test void ambiguousProviderResponseLeavesDurableClaimAndNeverResends() throws Exception {
        var p = retryPayment(99377);
        when(orderClient.getOrderById(p.getOrderId())).thenReturn(ResponseEntity.ok(retryOrder(p)));
        when(stripe.getSupportedCurrencies()).thenCallRealMethod();
        when(factory.getGateway("STRIPE")).thenReturn(new StripePaymentGateway(gatewayConfig, null, null, null));
        var posts = new AtomicInteger();
        var providerKey = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/payment_intents/pi_99377", exchange -> {
            boolean confirm = "POST".equals(exchange.getRequestMethod()) && exchange.getRequestURI().getPath().endsWith("/confirm");
            if (confirm) { posts.incrementAndGet(); providerKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key")); }
            String json = confirm ? "{\"error\":{\"type\":\"api_error\",\"message\":\"ambiguous\"}}"
                    : "{\"id\":\"pi_99377\",\"object\":\"payment_intent\",\"livemode\":false,\"currency\":\"usd\",\"amount\":2500,\"status\":\"requires_payment_method\"}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(confirm ? 500 : 200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        String base = Stripe.getApiBase(), key = Stripe.apiKey;
        server.start();
        try {
            Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); Stripe.apiKey = "sk_test_fixture";
            new TransactionTemplate(tx).executeWithoutResult(status -> {
                assertThrows(PaymentProcessingException.class,
                        () -> payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key"));
                status.setRollbackOnly();
            });
            assertEquals(1, posts.get());
            assertNotNull(providerKey.get());
            assertEquals("RESERVED", jdbc.queryForObject("select state from payment_retry_attempt where payment_id=?", String.class, p.getId()));
            assertEquals(providerKey.get(), jdbc.queryForObject("select provider_key::text from payment_retry_attempt where payment_id=?", String.class, p.getId()));
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                    () -> payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "other-key")).getStatusCode());
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                    () -> payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key")).getStatusCode());
            assertEquals(1, posts.get());
            assertEquals("RESERVED", jdbc.queryForObject("select state from payment_retry_attempt where payment_id=?", String.class, p.getId()));
        } finally { Stripe.overrideApiBase(base); Stripe.apiKey = key; server.stop(0); }
    }

    private Payment retryPayment(long orderId) {
        var p = payment(orderId, "STRIPE", PaymentStatus.FAILED);
        p.setStripeIntentCreatedByService(true); p.setStripeIntentLivemode(false);
        return repository.saveAndFlush(p);
    }

    private OrderDto retryOrder(Payment p) {
        var order = new OrderDto();
        order.setId(p.getOrderId()); order.setUserId(p.getUserId()); order.setStatus("PENDING");
        order.setInventoryReady(true); order.setPaymentRetryEligible(true); order.setCurrency("USD");
        var total = new OrderDto.MoneyDto(); total.setAmount(p.getAmount()); total.setCurrency("USD"); order.setTotalAmount(total);
        return order;
    }

    private void assertNoRetryOrProvider(long paymentId) {
        assertEquals(0, jdbc.queryForObject("select count(*) from payment_retry_attempt where payment_id=?", Integer.class, paymentId));
        verifyNoInteractions(stripe, factory);
    }

    @Test void retryAttemptClaimIsDurableBoundAndExclusive() throws Exception {
        var p = payment(99370, "STRIPE", PaymentStatus.FAILED);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(tx);
        var first = transaction.execute(s -> { var attempt = retryAttempts.claim(p.getId(), "key-a", "pm_a", p.getAmount(), "USD"); s.setRollbackOnly(); return attempt; });
        assertTrue(first.created());
        var replay = retryAttempts.claim(p.getId(), "key-a", "pm_a", p.getAmount(), "USD");
        assertFalse(replay.created()); assertEquals(first.providerKey(), replay.providerKey());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> retryAttempts.claim(p.getId(), "key-a", "pm_b", p.getAmount(), "USD"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> retryAttempts.claim(p.getId(), "key-a", "pm_a", new BigDecimal("26.00"), "USD"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> retryAttempts.claim(p.getId(), "key-a", "pm_a", p.getAmount(), "EUR"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> retryAttempts.claim(p.getId(), "key-b", "pm_a", p.getAmount(), "USD"));
        jdbc.update("update payment_retry_attempt set state='UNCERTAIN' where id=?", first.id());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> retryAttempts.claim(p.getId(), "key-b", "pm_a", p.getAmount(), "USD"));
        jdbc.update("update payment_retry_attempt set state='COMPLETED' where id=?", first.id());
        assertFalse(retryAttempts.claim(p.getId(), "key-a", "pm_a", p.getAmount(), "USD").created());
        var other = payment(99371, "STRIPE", PaymentStatus.FAILED);
        var start = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> a = () -> { start.await(); try { retryAttempts.claim(other.getId(), "key-1", "pm_a", other.getAmount(), "USD"); return true; } catch (org.springframework.web.server.ResponseStatusException e) { return false; } };
            java.util.concurrent.Callable<Boolean> b = () -> { start.await(); try { retryAttempts.claim(other.getId(), "key-2", "pm_a", other.getAmount(), "USD"); return true; } catch (org.springframework.web.server.ResponseStatusException e) { return false; } };
            var x = executor.submit(a); var y = executor.submit(b); start.countDown(); assertNotEquals(x.get(), y.get());
        } finally { executor.shutdownNow(); }
        var same = payment(99372, "STRIPE", PaymentStatus.FAILED);
        var gate = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<PaymentRetryAttempts.Attempt> call = () -> { gate.await(); return retryAttempts.claim(same.getId(), "same-key", "pm_a", same.getAmount(), "USD"); };
            var x = pool.submit(call); var y = pool.submit(call); gate.countDown();
            var one = x.get(); var two = y.get(); assertEquals(one.providerKey(), two.providerKey()); assertNotEquals(one.created(), two.created());
        } finally { pool.shutdownNow(); }
    }

    @Test void directUpdatesAndCancelRecordTransitions() {
        var p = payment(99302, "STRIPE", PaymentStatus.PENDING);
        payments.updatePaymentStatus(p.getId(), PaymentStatus.FAILED, "failed");
        payments.updatePaymentStatus(p.getId(), PaymentStatus.FAILED, "failed");
        payments.updatePaymentStatus(p.getId(), PaymentStatus.PENDING, null);
        payments.updatePaymentStatus(p.getId(), PaymentStatus.FAILED, "failed again");
        payments.cancelPayment(p.getId(), "cancelled");
        assertEquals(4, repository.findById(p.getId()).orElseThrow().getRevision());
        assertEquals(java.util.List.of("PaymentFailed", "PaymentFailed", "PaymentCanceled"),
                jdbc.queryForList("select event_type from payment_outbox where payment_id=? order by revision", String.class, p.getId()));
    }

    @Test void heldTransitionsAndOffOnDoNotBackfill() {
        var transaction = new org.springframework.transaction.support.TransactionTemplate(tx);
        var on = new PaymentOutbox(jdbc, new ObjectMapper(), true);
        var off = new PaymentOutbox(jdbc, new ObjectMapper(), false);
        for (var next : new PaymentStatus[]{PaymentStatus.SUCCEEDED, PaymentStatus.FAILED, PaymentStatus.CANCELED}) {
            var p = payment(99310 + next.ordinal(), "STRIPE", PaymentStatus.PENDING);
            transaction.executeWithoutResult(s -> {
                guard.hold(p.getOrderId());
                var managed = repository.findById(p.getId()).orElseThrow();
                on.transition(managed, next, true);
            });
            assertEquals("RECONCILIATION", jdbc.queryForObject("select state from payment_outbox where payment_id=?", String.class, p.getId()));
        }
        var p = payment(99350, "STRIPE", PaymentStatus.PENDING);
        transaction.executeWithoutResult(s -> {
            guard.lock(p.getOrderId());
            var managed = repository.findById(p.getId()).orElseThrow();
            off.transition(managed, PaymentStatus.FAILED, false);
            assertFalse(on.transition(managed, PaymentStatus.FAILED, false));
        });
        assertEquals(1, repository.findById(p.getId()).orElseThrow().getRevision());
        assertEquals(0, count(p.getId()));
    }

    @Test void transitionFoundationPersistsRevisionsAndRollsBack() throws Exception {
        var p = payment(99301, "STRIPE", PaymentStatus.PENDING);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(tx);
        var on = new PaymentOutbox(jdbc, new ObjectMapper(), true);
        var off = new PaymentOutbox(jdbc, new ObjectMapper(), false);
        transaction.executeWithoutResult(s -> {
            guard.lock(p.getOrderId());
            var managed = repository.findById(p.getId()).orElseThrow();
            assertTrue(on.transition(managed, PaymentStatus.FAILED, false));
            assertFalse(on.transition(managed, PaymentStatus.FAILED, false));
            assertTrue(off.transition(managed, PaymentStatus.PENDING, false));
            assertTrue(on.transition(managed, PaymentStatus.FAILED, true));
        });
        assertEquals(3, repository.findById(p.getId()).orElseThrow().getRevision());
        var rows = jdbc.queryForList("select revision,state,payload from payment_outbox where payment_id=? order by revision", p.getId());
        assertEquals(2, rows.size());
        assertEquals(1L, ((Number) rows.get(0).get("revision")).longValue());
        assertEquals(3L, ((Number) rows.get(1).get("revision")).longValue());
        assertEquals("RECONCILIATION", rows.get(1).get("state"));
        assertEquals("PENDING", new ObjectMapper().readTree((String) rows.get(1).get("payload")).path("previousStatus").asText());
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(s -> {
            guard.lock(p.getOrderId());
            on.transition(repository.findById(p.getId()).orElseThrow(), PaymentStatus.CANCELED, false);
            throw new IllegalStateException("rollback");
        }));
        assertEquals(3, repository.findById(p.getId()).orElseThrow().getRevision());
        assertEquals(PaymentStatus.FAILED, repository.findById(p.getId()).orElseThrow().getStatus());
        assertEquals(2, count(p.getId()));
    }

    @Test void notificationWriterPersistsImmutableSnapshotAndReconciliation() throws Exception {
        var writer = new PaymentNotificationOutbox(jdbc, new ObjectMapper(), validatorBean, true);
        var p = payment(99201, "STRIPE", PaymentStatus.PENDING);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(tx);
        transaction.executeWithoutResult(s -> writer.enqueue(p));
        String original = jdbc.queryForObject("select payload from payment_notification_outbox where payment_id=?", String.class, p.getId());
        var json = new ObjectMapper().readTree(original);
        assertEquals(12, json.size());
        java.util.UUID.fromString(json.path("eventId").asText());
        java.time.Instant.parse(json.path("occurredAt").asText());
        assertEquals("PaymentEmailRequested", json.path("eventType").asText());
        assertEquals(1, json.path("schemaVersion").asInt());
        assertEquals(p.getId().longValue(), json.path("paymentId").asLong());
        assertEquals(p.getPaymentNumber(), json.path("paymentNumber").asText());
        assertEquals(p.getOrderId().longValue(), json.path("orderId").asLong());
        assertEquals("owner", json.path("userId").asText());
        assertEquals("EMAIL", json.path("channel").asText());
        assertEquals("fixture@example.test", json.path("recipient").asText());
        assertTrue(json.path("content").asText().contains("25.00 USD"));
        p.setReceiptEmail("changed@example.test");
        transaction.executeWithoutResult(s -> writer.enqueue(p));
        assertEquals(original, jdbc.queryForObject("select payload from payment_notification_outbox where payment_id=?", String.class, p.getId()));
        var missing = payment(99202, "VNPAY", PaymentStatus.PENDING);
        missing.setReceiptEmail(null);
        transaction.executeWithoutResult(s -> writer.enqueue(missing));
        assertEquals("RECONCILIATION", jdbc.queryForObject("select state from payment_notification_outbox where payment_id=?", String.class, missing.getId()));
    }

    private org.de013.paymentservice.entity.Payment payment(long order, String provider, PaymentStatus status) {
        return repository.saveAndFlush(org.de013.paymentservice.entity.Payment.builder().orderId(order).userId("owner")
                .paymentNumber("TEST-" + order).amount(new BigDecimal("25.00"))
                .currency(org.de013.paymentservice.entity.enums.Currency.USD).method(org.de013.paymentservice.entity.enums.PaymentMethodType.CARD)
                .status(status).gatewayName(provider).stripePaymentIntentId("STRIPE".equals(provider) ? "pi_" + order : null)
                .providerAmountVnd("VNPAY".equals(provider) ? 625000L : null).providerCurrency("VNPAY".equals(provider) ? "VND" : null)
                .receiptEmail("fixture@example.test").build());
    }

    private void send(long order, String kind) throws Exception {
        String body = "{\"id\":\"evt_" + kind + order + "\",\"object\":\"event\",\"type\":\"payment_intent." + kind
                + "\",\"data\":{\"object\":{\"object\":\"payment_intent\",\"id\":\"pi_" + order + "\",\"status\":\"" + kind + "\"}}}";
        long time = java.time.Instant.now().getEpochSecond();
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(webhook.getWebhookSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = java.util.HexFormat.of().formatHex(mac.doFinal((time + "." + body).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        webhook.processStripeWebhook(body, "t=" + time + ",v1=" + signature);
    }

    private String event(long paymentId) {
        return jdbc.queryForObject("select payload from payment_outbox where payment_id=?", String.class, paymentId);
    }
    private int count(long paymentId) {
        return jdbc.queryForObject("select count(*) from payment_outbox where payment_id=?", Integer.class, paymentId);
    }
    private void revision(long id, long value, int events) {
        assertEquals(value, repository.findById(id).orElseThrow().getRevision());
        assertEquals(events, count(id));
    }

    @Test void providerEntryPointsPersistTransitions() throws Exception {
        when(factory.getGateway("STRIPE")).thenReturn(stripe);
        when(stripe.mapToGatewayPaymentRequest(any())).thenReturn(org.de013.paymentservice.dto.stripe.StripePaymentRequest.builder().build());
        when(stripe.createPaymentIntent(any())).thenReturn(org.de013.paymentservice.dto.stripe.StripePaymentResponse.builder().paymentIntentId("pi_99410").status("requires_action").build());
        payments.processPayment(org.de013.paymentservice.dto.payment.ProcessPaymentRequest.builder().orderId(99410L).userId("owner").amount(new BigDecimal("25.00"))
                .currency(org.de013.paymentservice.entity.enums.Currency.USD).paymentMethodType(org.de013.paymentservice.entity.enums.PaymentMethodType.CARD).provider("STRIPE").build());
        var created = repository.findByOrderId(99410L).get(0);
        revision(created.getId(), 1, 0);
        var confirm = payment(99411, "STRIPE", PaymentStatus.PENDING);
        when(stripe.confirmPaymentIntent("pi_99411", "pm_test")).thenReturn(org.de013.paymentservice.dto.stripe.StripePaymentResponse.builder().paymentIntentId("pi_99411").status("succeeded").build());
        payments.confirmPayment(confirm.getId(), "pm_test");
        revision(confirm.getId(), 1, 1); contract(event(confirm.getId()), confirm);
        var capture = payment(99412, "STRIPE", PaymentStatus.REQUIRES_CONFIRMATION);
        when(stripe.capturePayment(eq("pi_99412"), any())).thenReturn(org.de013.paymentservice.dto.stripe.StripePaymentResponse.builder().paymentIntentId("pi_99412").status("succeeded").build());
        payments.capturePayment(capture.getId(), null);
        revision(capture.getId(), 1, 1); contract(event(capture.getId()), capture);
        var sync = payment(99413, "STRIPE", PaymentStatus.PENDING);
        when(stripe.getPaymentIntent("pi_99413")).thenReturn(org.de013.paymentservice.dto.stripe.StripePaymentResponse.builder().paymentIntentId("pi_99413").status("succeeded").build());
        payments.syncPaymentStatusWithStripe(sync.getId()); payments.syncPaymentStatusWithStripe(sync.getId());
        revision(sync.getId(), 1, 1); contract(event(sync.getId()), sync);
    }

    @Test void webhookAndVnpayFailureTransitions() throws Exception {
        var p = payment(99420, "STRIPE", PaymentStatus.PENDING);
        send(99420, "requires_action"); revision(p.getId(), 1, 0);
        send(99420, "payment_failed"); revision(p.getId(), 2, 1);
        var failure = new ObjectMapper().readTree(event(p.getId()));
        assertEquals("PaymentFailed", failure.path("eventType").asText());
        assertEquals("REQUIRES_ACTION", failure.path("previousStatus").asText());
        assertEquals(2, failure.path("revision").asLong());
        var cfg = gatewayConfig.getGateways().getVnpay();
        var realGateway = new org.de013.paymentservice.gateway.vnpay.VnpayPaymentGateway(gatewayConfig);
        when(vnpayGateway.verifyCallback(anyMap())).thenAnswer(i -> realGateway.verifyCallback(i.getArgument(0)));
        var vn = payment(99421, "VNPAY", PaymentStatus.PENDING);
        var fields = new java.util.TreeMap<String, String>();
        fields.put("vnp_TmnCode", cfg.getTmnCode()); fields.put("vnp_TxnRef", vn.getPaymentNumber()); fields.put("vnp_Amount", "62500000");
        fields.put("vnp_ResponseCode", "01"); fields.put("vnp_TransactionStatus", "01");
        fields.put("vnp_SecureHash", org.de013.paymentservice.gateway.vnpay.VnpayUtils.hmacSHA512(cfg.getHashSecret(), org.de013.paymentservice.gateway.vnpay.VnpayUtils.buildHashData(fields)));
        assertEquals("00", ipn.process(fields).get("RspCode"));
        assertEquals("00", ipn.process(fields).get("RspCode"));
        revision(vn.getId(), 1, 1);
        var vne = new ObjectMapper().readTree(event(vn.getId()));
        assertEquals("PaymentFailed", vne.path("eventType").asText()); assertEquals("PENDING", vne.path("previousStatus").asText());
    }
    private void contract(String payload, org.de013.paymentservice.entity.Payment p) throws Exception {
        var e = new ObjectMapper().readTree(payload);
        assertEquals(10, e.size()); java.util.UUID.fromString(e.path("eventId").asText()); java.time.Instant.parse(e.path("occurredAt").asText());
        assertEquals("PaymentSucceeded", e.path("eventType").asText()); assertEquals(1, e.path("schemaVersion").asInt());
        assertEquals(p.getId().longValue(), e.path("paymentId").asLong()); assertEquals(p.getOrderId().longValue(), e.path("orderId").asLong());
        assertEquals(p.getPaymentNumber(), e.path("paymentNumber").asText()); assertEquals(p.getGatewayName(), e.path("provider").asText());
        assertTrue(e.path("amount").isNumber()); assertEquals(0, p.getAmount().compareTo(e.path("amount").decimalValue()));
        assertEquals(p.getCurrency().name(), e.path("currency").asText());
    }

    @Test void stripeApiAndSignedWebhookDedupeAndHistoricalSuccessDoesNotBackfill() throws Exception {
        when(factory.getGateway("STRIPE")).thenReturn(stripe);
        var current = payment(99101, "STRIPE", PaymentStatus.PENDING);
        when(stripe.getPaymentIntent("pi_99101")).thenReturn(org.de013.paymentservice.dto.stripe.StripePaymentResponse.builder().paymentIntentId("pi_99101").status("succeeded").build());
        payments.syncPaymentStatusWithStripe(current.getId());
        revision(current.getId(), 1, 1);
        String payload = event(current.getId());
        contract(payload, current);
        var json = new ObjectMapper().readTree(payload);
        assertEquals("PaymentSucceeded", json.path("eventType").asText());
        assertEquals("STRIPE", json.path("provider").asText());
        assertEquals(99101, json.path("orderId").asLong());
        assertEquals(0, json.path("amount").decimalValue().compareTo(new BigDecimal("25.00")));
        send(99101, "succeeded");
        assertEquals(1, count(current.getId())); assertEquals(payload, event(current.getId()));
        var historical = payment(99102, "STRIPE", PaymentStatus.SUCCEEDED);
        when(stripe.getPaymentIntent("pi_99102")).thenReturn(org.de013.paymentservice.dto.stripe.StripePaymentResponse.builder().paymentIntentId("pi_99102").status("succeeded").build());
        payments.syncPaymentStatusWithStripe(historical.getId());
        assertEquals(0, count(historical.getId()));
        var webhookOnly = payment(99107, "STRIPE", PaymentStatus.PENDING);
        send(99107, "succeeded"); contract(event(webhookOnly.getId()), webhookOnly);
        revision(webhookOnly.getId(), 1, 1);
        verify(orderClient, never()).markOrderAsPaid(anyLong(), anyLong(), anyString());
    }

    @Test void signedVnpayReplayAndHeldAndReleasedSuccessKeepEvidence() throws Exception {
        var cfg = gatewayConfig.getGateways().getVnpay();
        var realGateway = new org.de013.paymentservice.gateway.vnpay.VnpayPaymentGateway(gatewayConfig);
        when(vnpayGateway.verifyCallback(anyMap())).thenAnswer(i -> realGateway.verifyCallback(i.getArgument(0)));
        var p = payment(99103, "VNPAY", PaymentStatus.PENDING);
        var fields = new java.util.TreeMap<String, String>();
        fields.put("vnp_TmnCode", cfg.getTmnCode()); fields.put("vnp_TxnRef", p.getPaymentNumber());
        fields.put("vnp_Amount", "62500000"); fields.put("vnp_ResponseCode", "00"); fields.put("vnp_TransactionStatus", "00");
        fields.put("vnp_SecureHash", org.de013.paymentservice.gateway.vnpay.VnpayUtils.hmacSHA512(cfg.getHashSecret(), org.de013.paymentservice.gateway.vnpay.VnpayUtils.buildHashData(fields)));
        assertEquals("00", ipn.process(fields).get("RspCode")); String original = event(p.getId()); contract(original, p);
        revision(p.getId(), 1, 1);
        assertEquals("VNPAY", new ObjectMapper().readTree(original).path("provider").asText());
        assertEquals("02", ipn.process(fields).get("RspCode")); assertEquals(original, event(p.getId()));
        var heldVnpay = payment(99106, "VNPAY", PaymentStatus.PENDING);
        new org.springframework.transaction.support.TransactionTemplate(tx).executeWithoutResult(s -> guard.hold(99106L));
        fields.put("vnp_TxnRef", heldVnpay.getPaymentNumber()); fields.remove("vnp_SecureHash");
        fields.put("vnp_SecureHash", org.de013.paymentservice.gateway.vnpay.VnpayUtils.hmacSHA512(cfg.getHashSecret(), org.de013.paymentservice.gateway.vnpay.VnpayUtils.buildHashData(fields)));
        assertEquals("00", ipn.process(fields).get("RspCode"));
        assertEquals(PaymentStatus.SUCCEEDED, repository.findById(heldVnpay.getId()).orElseThrow().getStatus()); assertEquals(1, count(heldVnpay.getId()));
        assertEquals("RECONCILIATION", jdbc.queryForObject("select state from payment_outbox where payment_id=?", String.class, heldVnpay.getId()));
        var released = payment(99104, "STRIPE", PaymentStatus.PENDING);
        send(99104, "canceled"); send(99104, "succeeded");
        revision(released.getId(), 2, 2);
        assertEquals(PaymentStatus.SUCCEEDED, repository.findById(released.getId()).orElseThrow().getStatus());
        assertTrue(jdbc.queryForObject("select reconciliation_hold from payment_order_guard where order_id=?", Boolean.class, 99104L));
        assertEquals(2, count(released.getId()));
        var held = hostedStore.reserve(99105L, "owner", new BigDecimal("25.00"), "https://fixture.test/checkout/result");
        new org.springframework.transaction.support.TransactionTemplate(tx).executeWithoutResult(s -> guard.hold(99105L));
        hostedStore.record(99105L, 0, "cs_99105", "PAID", "pi_99105");
        assertEquals(PaymentStatus.SUCCEEDED, repository.findById(held.getPaymentId()).orElseThrow().getStatus());
        assertEquals(1, count(held.getPaymentId()));
        assertEquals("RECONCILIATION", jdbc.queryForObject("select state from payment_outbox where payment_id=?", String.class, held.getPaymentId()));
        verify(orderClient, never()).markOrderAsPaid(anyLong(), anyLong(), anyString());
    }
    @Test void hostedRecordPersistsExactEventAndRepeatedReadKeepsItStable() throws Exception {
        var checkout = hostedStore.reserve(99001L, "owner", new BigDecimal("25.00"), "https://fixture.test/checkout/result");
        hostedStore.record(99001L, 0, "cs_99001", "OPEN", "pi_99001");
        revision(checkout.getPaymentId(), 0, 0);
        hostedStore.record(99001L, 0, "cs_99001", "PAID", "pi_99001");
        revision(checkout.getPaymentId(), 1, 1);
        String payload = jdbc.queryForObject("select payload from payment_outbox where payment_id=?", String.class, checkout.getPaymentId());
        contract(payload, repository.findById(checkout.getPaymentId()).orElseThrow());
        var event = new ObjectMapper().readTree(payload);
        assertEquals(10, event.size());
        assertDoesNotThrow(() -> java.util.UUID.fromString(event.path("eventId").asText()));
        assertDoesNotThrow(() -> java.time.Instant.parse(event.path("occurredAt").asText()));
        assertEquals("PaymentSucceeded", event.path("eventType").asText());
        assertEquals(1, event.path("schemaVersion").asInt());
        assertEquals(checkout.getPaymentId().longValue(), event.path("paymentId").asLong());
        assertEquals("HOSTED-99001", event.path("paymentNumber").asText());
        assertEquals(99001L, event.path("orderId").asLong());
        assertEquals("STRIPE", event.path("provider").asText());
        assertEquals(0, new BigDecimal("25.00").compareTo(event.path("amount").decimalValue()));
        assertEquals("USD", event.path("currency").asText());
        hostedStore.record(99001L, 0, "cs_99001", "PAID", "pi_99001");
        revision(checkout.getPaymentId(), 1, 1);
        hostedStore.markPaid(99001L);
        assertEquals(payload, jdbc.queryForObject("select payload from payment_outbox where payment_id=?", String.class, checkout.getPaymentId()));
        verify(orders, never()).markPaid(anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test void insertFailureRollsBackHostedEvidenceButNotReservation() {
        var checkout = hostedStore.reserve(99002L, "owner", new BigDecimal("25.00"), "https://fixture.test/checkout/result");
        jdbc.execute("create function reject_outbox_stage_a() returns trigger language plpgsql as $$ begin raise exception 'outbox failure'; end $$");
        jdbc.execute("create trigger reject_outbox_stage_a before insert on payment_outbox for each row execute function reject_outbox_stage_a()");
        try {
            assertThrows(Exception.class, () -> hostedStore.record(99002L, 0, "cs_99002", "PAID", "pi_99002"));
            assertEquals(PaymentStatus.PENDING, repository.findById(checkout.getPaymentId()).orElseThrow().getStatus());
            assertEquals(0, jdbc.queryForObject("select count(*) from payment_outbox where payment_id=?", Integer.class, checkout.getPaymentId()));
        } finally {
            jdbc.execute("drop trigger reject_outbox_stage_a on payment_outbox");
            jdbc.execute("drop function reject_outbox_stage_a()");
        }
    }

    @Test void staleRetryWebhooksDoNotDowngradeVerifiedProviderProgress() throws Exception {
        var currentStatus = new AtomicReference<>("processing");
        var gets = new AtomicInteger();
        var mutations = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/payment_intents/", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) gets.incrementAndGet(); else mutations.incrementAndGet();
            String intent = exchange.getRequestURI().getPath().substring("/v1/payment_intents/".length());
            byte[] body = ("{\"id\":\"" + intent + "\",\"object\":\"payment_intent\",\"livemode\":false,\"currency\":\"usd\",\"amount\":2500,\"status\":\"" + currentStatus.get() + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        when(stripe.getSupportedCurrencies()).thenCallRealMethod();
        String base = Stripe.getApiBase(), key = Stripe.apiKey;
        server.start();
        try {
            Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); Stripe.apiKey = "sk_test_fixture";
            var processing = retryPayment(99378); processing.setStatus(PaymentStatus.PROCESSING); repository.saveAndFlush(processing);
            retryAttempts.claim(processing.getId(), "key", "pm_retry", processing.getAmount(), "USD");
            send(99378, "payment_failed");
            assertEquals(PaymentStatus.PROCESSING, repository.findById(processing.getId()).orElseThrow().getStatus());
            revision(processing.getId(), 0, 0);
            assertEquals(1, jdbc.queryForObject("select count(*) from processed_stripe_events where event_id=?", Integer.class, "evt_payment_failed99378"));

            currentStatus.set("succeeded");
            var action = retryPayment(99379); action.setStatus(PaymentStatus.REQUIRES_ACTION); repository.saveAndFlush(action);
            retryAttempts.claim(action.getId(), "key", "pm_retry", action.getAmount(), "USD");
            send(99379, "payment_failed");
            assertEquals(PaymentStatus.REQUIRES_ACTION, repository.findById(action.getId()).orElseThrow().getStatus());
            revision(action.getId(), 0, 0);
            assertEquals(1, jdbc.queryForObject("select count(*) from processed_stripe_events where event_id=?", Integer.class, "evt_payment_failed99379"));

            var progress = retryPayment(99380); progress.setStatus(PaymentStatus.PROCESSING); repository.saveAndFlush(progress);
            retryAttempts.claim(progress.getId(), "key", "pm_retry", progress.getAmount(), "USD");
            send(99380, "requires_action");
            assertEquals(PaymentStatus.PROCESSING, repository.findById(progress.getId()).orElseThrow().getStatus());
            revision(progress.getId(), 0, 0);
            assertEquals(1, jdbc.queryForObject("select count(*) from processed_stripe_events where event_id=?", Integer.class, "evt_requires_action99380"));
            assertEquals(3, gets.get()); assertEquals(0, mutations.get());
        } finally { Stripe.overrideApiBase(base); Stripe.apiKey = key; server.stop(0); }
    }

    @Test void staleRetryWebhookProviderFailureRollsBackDedupe() throws Exception {
        var p = retryPayment(99381); p.setStatus(PaymentStatus.PROCESSING); repository.saveAndFlush(p);
        retryAttempts.claim(p.getId(), "key", "pm_retry", p.getAmount(), "USD");
        when(stripe.getSupportedCurrencies()).thenCallRealMethod();
        var gets = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/payment_intents/pi_99381", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) gets.incrementAndGet();
            byte[] body = "{\"error\":{\"type\":\"api_error\",\"message\":\"unavailable\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(503, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        String base = Stripe.getApiBase(), key = Stripe.apiKey;
        server.start();
        try {
            Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); Stripe.apiKey = "sk_test_fixture";
            assertThrows(RuntimeException.class, () -> send(99381, "payment_failed"));
            assertEquals(1, gets.get());
            assertEquals(PaymentStatus.PROCESSING, repository.findById(p.getId()).orElseThrow().getStatus());
            revision(p.getId(), 0, 0);
            assertEquals(0, jdbc.queryForObject("select count(*) from processed_stripe_events where event_id=?", Integer.class, "evt_payment_failed99381"));
        } finally { Stripe.overrideApiBase(base); Stripe.apiKey = key; server.stop(0); }
    }

    @Test void retryPostConnectionCloseDoesNotResend() throws Exception {
        var p = retryPayment(99382);
        when(orderClient.getOrderById(p.getOrderId())).thenReturn(ResponseEntity.ok(retryOrder(p)));
        when(stripe.getSupportedCurrencies()).thenCallRealMethod();
        when(factory.getGateway("STRIPE")).thenReturn(new StripePaymentGateway(gatewayConfig, null, null, null));
        var posts = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/payment_intents/pi_99382", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) { posts.incrementAndGet(); exchange.close(); return; }
            byte[] body = "{\"id\":\"pi_99382\",\"object\":\"payment_intent\",\"livemode\":false,\"currency\":\"usd\",\"amount\":2500,\"status\":\"requires_payment_method\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        String base = Stripe.getApiBase(), key = Stripe.apiKey;
        server.start();
        try {
            Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); Stripe.apiKey = "sk_test_fixture";
            assertThrows(PaymentProcessingException.class, () -> payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key"));
            assertEquals(1, posts.get());
            assertEquals("RESERVED", jdbc.queryForObject("select state from payment_retry_attempt where payment_id=?", String.class, p.getId()));
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                    () -> payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "other-key")).getStatusCode());
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                    () -> payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key")).getStatusCode());
            assertEquals(1, posts.get());
            assertEquals("RESERVED", jdbc.queryForObject("select state from payment_retry_attempt where payment_id=?", String.class, p.getId()));
        } finally { Stripe.overrideApiBase(base); Stripe.apiKey = key; server.stop(0); }
    }

    @Test void providerSuccessThenDatabaseRollbackRecoversByReadOnlyReplayUnderHold() throws Exception {
        var p = retryPayment(99385);
        var order = retryOrder(p);
        when(orderClient.getOrderById(p.getOrderId())).thenReturn(ResponseEntity.ok(order));
        when(stripe.getSupportedCurrencies()).thenCallRealMethod();
        when(factory.getGateway("STRIPE")).thenReturn(new StripePaymentGateway(gatewayConfig, null, null, null));
        var providerStatus = new AtomicReference<>("requires_payment_method");
        var providerKey = new AtomicReference<String>();
        var posts = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/payment_intents/pi_99385", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                posts.incrementAndGet(); providerKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                providerStatus.set("succeeded");
            }
            byte[] body = ("{\"id\":\"pi_99385\",\"object\":\"payment_intent\",\"livemode\":false,\"currency\":\"usd\",\"amount\":2500,\"status\":\"" + providerStatus.get() + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        String base = Stripe.getApiBase(), key = Stripe.apiKey;
        server.start();
        try {
            Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); Stripe.apiKey = "sk_test_fixture";
            jdbc.execute("create function reject_retry_success_outbox() returns trigger language plpgsql as $$ begin raise exception 'retry outbox failure'; end $$");
            try {
                jdbc.execute("create trigger reject_retry_success_outbox before insert on payment_outbox for each row execute function reject_retry_success_outbox()");
                assertThrows(RuntimeException.class, () -> payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key"));
            } finally {
                jdbc.execute("drop trigger if exists reject_retry_success_outbox on payment_outbox");
                jdbc.execute("drop function if exists reject_retry_success_outbox()");
            }
            assertEquals(1, posts.get());
            assertNotNull(providerKey.get());
            assertEquals(providerKey.get(), jdbc.queryForObject("select provider_key::text from payment_retry_attempt where payment_id=? and idempotency_key=?", String.class, p.getId(), "key"));
            assertEquals("RESERVED", jdbc.queryForObject("select state from payment_retry_attempt where payment_id=?", String.class, p.getId()));
            assertEquals(PaymentStatus.FAILED, repository.findById(p.getId()).orElseThrow().getStatus());
            revision(p.getId(), 0, 0);

            new TransactionTemplate(tx).executeWithoutResult(status -> guard.hold(p.getOrderId()));
            order.setPaymentRetryEligible(false);
            clearInvocations(orderClient);
            var replay = payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key");
            assertEquals(p.getId(), replay.getId());
            assertEquals("pi_99385", replay.getStripePaymentIntentId());
            assertEquals(PaymentStatus.SUCCEEDED, replay.getStatus());
            assertEquals(PaymentStatus.SUCCEEDED, repository.findById(p.getId()).orElseThrow().getStatus());
            assertEquals(1, posts.get());
            assertEquals("RECONCILIATION", jdbc.queryForObject("select state from payment_outbox where payment_id=?", String.class, p.getId()));
            verifyNoInteractions(orderClient);
        } finally { Stripe.overrideApiBase(base); Stripe.apiKey = key; server.stop(0); }
    }

    @Test void retryRechecksDeadlineAfterProviderReadBeforeMutation() throws Exception {
        var p = retryPayment(99386);
        var eligible = new java.util.concurrent.atomic.AtomicBoolean(true);
        when(orderClient.getOrderById(p.getOrderId())).thenAnswer(invocation -> {
            var order = retryOrder(p); order.setPaymentRetryEligible(eligible.get()); return ResponseEntity.ok(order);
        });
        when(stripe.getSupportedCurrencies()).thenCallRealMethod();
        var posts = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/payment_intents/pi_99386", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) posts.incrementAndGet(); else eligible.set(false);
            byte[] body = "{\"id\":\"pi_99386\",\"object\":\"payment_intent\",\"livemode\":false,\"currency\":\"usd\",\"amount\":2500,\"status\":\"requires_payment_method\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        String base = Stripe.getApiBase(), key = Stripe.apiKey;
        server.start();
        try {
            Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); Stripe.apiKey = "sk_test_fixture";
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                    () -> payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key")).getStatusCode());
            assertEquals(0, posts.get());
            assertEquals("REJECTED", jdbc.queryForObject("select state from payment_retry_attempt where payment_id=?", String.class, p.getId()));
            assertFalse(retryAttempts.active(p.getId()));
            assertEquals(PaymentStatus.FAILED, repository.findById(p.getId()).orElseThrow().getStatus());
        } finally { Stripe.overrideApiBase(base); Stripe.apiKey = key; server.stop(0); }
    }

    @Test void concurrentSameKeyRetrySendsOneProviderConfirm() throws Exception {
        concurrentRetries(99387, true);
    }

    @Test void concurrentDifferentKeyRetrySendsOneProviderConfirm() throws Exception {
        concurrentRetries(99388, false);
    }

    private void concurrentRetries(long orderId, boolean sameKey) throws Exception {
        var p = retryPayment(orderId);
        when(orderClient.getOrderById(p.getOrderId())).thenReturn(ResponseEntity.ok(retryOrder(p)));
        when(stripe.getSupportedCurrencies()).thenCallRealMethod();
        when(factory.getGateway("STRIPE")).thenReturn(new StripePaymentGateway(gatewayConfig, null, null, null));
        var posts = new AtomicInteger();
        var providerKey = new AtomicReference<String>();
        var providerStatus = new AtomicReference<>("requires_payment_method");
        var postEntered = new java.util.concurrent.CountDownLatch(1);
        var releasePost = new java.util.concurrent.CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/payment_intents/pi_" + orderId, exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                posts.incrementAndGet(); providerKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                postEntered.countDown();
                try { if (!releasePost.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new java.io.IOException("POST release timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new java.io.IOException(e); }
                providerStatus.set("succeeded");
            }
            byte[] body = ("{\"id\":\"pi_" + orderId + "\",\"object\":\"payment_intent\",\"livemode\":false,\"currency\":\"usd\",\"amount\":2500,\"status\":\"" + providerStatus.get() + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        String base = Stripe.getApiBase(), key = Stripe.apiKey;
        server.start();
        try {
            Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); Stripe.apiKey = "sk_test_fixture";
            java.util.concurrent.Callable<Object> first = () -> { start.await(); try { return payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key-a"); } catch (RuntimeException e) { return e; } };
            java.util.concurrent.Callable<Object> second = () -> { start.await(); try { return payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", sameKey ? "key-a" : "key-b"); } catch (RuntimeException e) { return e; } };
            var one = pool.submit(first); var two = pool.submit(second); start.countDown();
            assertTrue(postEntered.await(10, java.util.concurrent.TimeUnit.SECONDS));
            releasePost.countDown();
            Object a = one.get(15, java.util.concurrent.TimeUnit.SECONDS), b = two.get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(1, posts.get());
            assertNotNull(providerKey.get());
            assertEquals(providerKey.get(), jdbc.queryForObject("select provider_key::text from payment_retry_attempt where payment_id=?", String.class, p.getId()));
            assertTrue(a instanceof org.de013.paymentservice.dto.payment.PaymentResponse || b instanceof org.de013.paymentservice.dto.payment.PaymentResponse);
            Object other = a instanceof RuntimeException ? a : b instanceof RuntimeException ? b : null;
            if (!sameKey) assertEquals(HttpStatus.CONFLICT, assertInstanceOf(ResponseStatusException.class, other).getStatusCode());
            else if (other != null) assertEquals(HttpStatus.CONFLICT, assertInstanceOf(ResponseStatusException.class, other).getStatusCode());
            String winningKey = jdbc.queryForObject("select idempotency_key from payment_retry_attempt where payment_id=?", String.class, p.getId());
            assertEquals(p.getId(), payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", winningKey).getId());
            assertEquals(1, posts.get());
        } finally { releasePost.countDown(); pool.shutdownNow(); Stripe.overrideApiBase(base); Stripe.apiKey = key; server.stop(0); }
    }

    @Test void retryReadTimeoutAfterPostDoesNotResend() throws Exception {
        var p = retryPayment(99389);
        when(orderClient.getOrderById(p.getOrderId())).thenReturn(ResponseEntity.ok(retryOrder(p)));
        when(stripe.getSupportedCurrencies()).thenCallRealMethod();
        when(factory.getGateway("STRIPE")).thenReturn(new StripePaymentGateway(gatewayConfig, null, null, null));
        var posts = new AtomicInteger();
        var postEntered = new java.util.concurrent.CountDownLatch(1);
        var releasePost = new java.util.concurrent.CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/v1/payment_intents/pi_99389", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                posts.incrementAndGet(); postEntered.countDown();
                try { releasePost.await(10, java.util.concurrent.TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                exchange.close(); return;
            }
            byte[] body = "{\"id\":\"pi_99389\",\"object\":\"payment_intent\",\"livemode\":false,\"currency\":\"usd\",\"amount\":2500,\"status\":\"requires_payment_method\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        String base = Stripe.getApiBase(), key = Stripe.apiKey;
        int readTimeout = Stripe.getReadTimeout();
        server.start();
        try {
            Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); Stripe.apiKey = "sk_test_fixture"; Stripe.setReadTimeout(200);
            var result = pool.submit(() -> assertThrows(PaymentProcessingException.class,
                    () -> payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key")));
            assertTrue(postEntered.await(10, java.util.concurrent.TimeUnit.SECONDS));
            var error = result.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(java.util.stream.Stream.iterate((Throwable) error, java.util.Objects::nonNull, Throwable::getCause)
                    .anyMatch(cause -> cause instanceof java.net.SocketTimeoutException));
            assertEquals(1, posts.get());
            assertEquals("RESERVED", jdbc.queryForObject("select state from payment_retry_attempt where payment_id=?", String.class, p.getId()));
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                    () -> payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key")).getStatusCode());
            assertEquals(1, posts.get());
        } finally {
            releasePost.countDown(); pool.shutdownNow(); Stripe.setReadTimeout(readTimeout);
            Stripe.overrideApiBase(base); Stripe.apiKey = key; server.stop(0);
            ((java.util.concurrent.ExecutorService) server.getExecutor()).shutdownNow();
        }
    }

    @Test void closureWaitsForInFlightRetryAndHoldsAfterSuccess() throws Exception {
        var p = retryPayment(99390);
        when(orderClient.getOrderById(p.getOrderId())).thenReturn(ResponseEntity.ok(retryOrder(p)));
        when(stripe.getSupportedCurrencies()).thenCallRealMethod();
        when(factory.getGateway("STRIPE")).thenReturn(new StripePaymentGateway(gatewayConfig, null, null, null));
        var postEntered = new java.util.concurrent.CountDownLatch(1);
        var releasePost = new java.util.concurrent.CountDownLatch(1);
        var closureStarted = new java.util.concurrent.CountDownLatch(1);
        var confirms = new AtomicInteger();
        var cancels = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/payment_intents/pi_99390", exchange -> {
            boolean post = "POST".equals(exchange.getRequestMethod());
            if (post && exchange.getRequestURI().getPath().endsWith("/confirm")) {
                confirms.incrementAndGet(); postEntered.countDown();
                try { if (!releasePost.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new java.io.IOException("POST release timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new java.io.IOException(e); }
            } else if (post) cancels.incrementAndGet();
            String status = post ? "succeeded" : "requires_payment_method";
            byte[] body = ("{\"id\":\"pi_99390\",\"object\":\"payment_intent\",\"livemode\":false,\"currency\":\"usd\",\"amount\":2500,\"status\":\"" + status + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        String base = Stripe.getApiBase(), key = Stripe.apiKey;
        server.start();
        try {
            Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); Stripe.apiKey = "sk_test_fixture";
            var retry = pool.submit(() -> payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key"));
            assertTrue(postEntered.await(10, java.util.concurrent.TimeUnit.SECONDS));
            var closure = pool.submit(() -> { closureStarted.countDown(); return closures.close(p.getOrderId(), java.util.UUID.randomUUID()); });
            assertTrue(closureStarted.await(10, java.util.concurrent.TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> closure.get(300, java.util.concurrent.TimeUnit.MILLISECONDS));
            releasePost.countDown();
            assertEquals(PaymentStatus.SUCCEEDED, retry.get(10, java.util.concurrent.TimeUnit.SECONDS).getStatus());
            assertEquals("HOLD", closure.get(10, java.util.concurrent.TimeUnit.SECONDS).status());
            var stored = repository.findById(p.getId()).orElseThrow();
            assertEquals(PaymentStatus.SUCCEEDED, stored.getStatus());
            assertFalse(stored.isVerifiedRelease());
            assertEquals(1, confirms.get()); assertEquals(0, cancels.get());
        } finally { releasePost.countDown(); pool.shutdownNow(); Stripe.overrideApiBase(base); Stripe.apiKey = key; server.stop(0); }
    }

    @Test void retryRejectsMismatchedSuccessfulProviderResponseAndRetainsFence() throws Exception {
        var posts = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/payment_intents/", exchange -> {
            String[] path = exchange.getRequestURI().getPath().split("/");
            String id = path[3];
            boolean post = "POST".equals(exchange.getRequestMethod());
            if (post) posts.incrementAndGet();
            String mode = post && id.endsWith("99392") ? "true" : "false";
            String currency = post && id.endsWith("99393") ? "eur" : "usd";
            int amount = post && id.endsWith("99391") ? 2501 : 2500;
            byte[] body = ("{\"id\":\"" + id + "\",\"object\":\"payment_intent\",\"livemode\":" + mode + ",\"currency\":\"" + currency + "\",\"amount\":" + amount + ",\"status\":\"" + (post ? "succeeded" : "requires_payment_method") + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        when(stripe.getSupportedCurrencies()).thenCallRealMethod();
        when(factory.getGateway("STRIPE")).thenReturn(new StripePaymentGateway(gatewayConfig, null, null, null));
        String base = Stripe.getApiBase(), key = Stripe.apiKey;
        server.start();
        try {
            Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); Stripe.apiKey = "sk_test_fixture";
            for (long orderId = 99391; orderId <= 99393; orderId++) {
                var p = retryPayment(orderId);
                when(orderClient.getOrderById(orderId)).thenReturn(ResponseEntity.ok(retryOrder(p)));
                int before = posts.get();
                assertThrows(PaymentProcessingException.class, () -> payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key"));
                assertEquals(before + 1, posts.get());
                assertEquals(PaymentStatus.FAILED, repository.findById(p.getId()).orElseThrow().getStatus());
                revision(p.getId(), 0, 0);
                assertEquals("RESERVED", jdbc.queryForObject("select state from payment_retry_attempt where payment_id=?", String.class, p.getId()));
                assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                        () -> payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "another-key")).getStatusCode());
                assertEquals(before + 1, posts.get());
            }
        } finally { Stripe.overrideApiBase(base); Stripe.apiKey = key; server.stop(0); }
    }

    @Test void retrySuccessWebhookRequiresCurrentVerifiedFinancialIdentity() throws Exception {
        var p = retryPayment(99394);
        when(orderClient.getOrderById(p.getOrderId())).thenReturn(ResponseEntity.ok(retryOrder(p)));
        when(stripe.getSupportedCurrencies()).thenCallRealMethod();
        when(factory.getGateway("STRIPE")).thenReturn(new StripePaymentGateway(gatewayConfig, null, null, null));
        var amount = new AtomicInteger(2500);
        var providerStatus = new AtomicReference<>("requires_payment_method");
        var posts = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/payment_intents/pi_99394", exchange -> {
            boolean post = "POST".equals(exchange.getRequestMethod());
            if (post) { posts.incrementAndGet(); amount.set(2501); providerStatus.set("succeeded"); }
            byte[] body = ("{\"id\":\"pi_99394\",\"object\":\"payment_intent\",\"livemode\":false,\"currency\":\"usd\",\"amount\":" + amount.get() + ",\"status\":\"" + providerStatus.get() + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        String base = Stripe.getApiBase(), key = Stripe.apiKey;
        server.start();
        try {
            Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); Stripe.apiKey = "sk_test_fixture";
            assertThrows(PaymentProcessingException.class, () -> payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key"));
            assertEquals(1, posts.get());
            assertEquals("RESERVED", jdbc.queryForObject("select state from payment_retry_attempt where payment_id=?", String.class, p.getId()));
            new TransactionTemplate(tx).executeWithoutResult(status -> guard.hold(p.getOrderId()));
            clearInvocations(orderClient);
            assertThrows(RuntimeException.class, () -> send(99394, "succeeded"));
            assertEquals(PaymentStatus.FAILED, repository.findById(p.getId()).orElseThrow().getStatus());
            revision(p.getId(), 0, 0);
            assertEquals(0, jdbc.queryForObject("select count(*) from processed_stripe_events where event_id=?", Integer.class, "evt_succeeded99394"));
            verify(orderClient, never()).markOrderAsPaid(anyLong(), anyLong(), anyString());
            amount.set(2500); providerStatus.set("processing");
            assertThrows(RuntimeException.class, () -> send(99394, "succeeded"));
            assertEquals(PaymentStatus.FAILED, repository.findById(p.getId()).orElseThrow().getStatus());
            revision(p.getId(), 0, 0);
            assertEquals(0, jdbc.queryForObject("select count(*) from processed_stripe_events where event_id=?", Integer.class, "evt_succeeded99394"));
            providerStatus.set("succeeded");
            send(99394, "succeeded"); send(99394, "succeeded");
            assertEquals(PaymentStatus.SUCCEEDED, repository.findById(p.getId()).orElseThrow().getStatus());
            revision(p.getId(), 1, 1);
            assertEquals("RECONCILIATION", jdbc.queryForObject("select state from payment_outbox where payment_id=?", String.class, p.getId()));
            assertEquals(1, jdbc.queryForObject("select count(*) from processed_stripe_events where event_id=?", Integer.class, "evt_succeeded99394"));
            assertEquals(1, posts.get());
            verify(orderClient, never()).markOrderAsPaid(anyLong(), anyLong(), anyString());
        } finally { Stripe.overrideApiBase(base); Stripe.apiKey = key; server.stop(0); }
    }

    @Test void retrySyncRequiresVerifiedCurrentFinancialIdentity() throws Exception {
        var p = retryPayment(99395);
        when(orderClient.getOrderById(p.getOrderId())).thenReturn(ResponseEntity.ok(retryOrder(p)));
        when(stripe.getSupportedCurrencies()).thenCallRealMethod();
        when(factory.getGateway("STRIPE")).thenReturn(new StripePaymentGateway(gatewayConfig, null, null, null));
        var amount = new AtomicInteger(2500);
        var providerStatus = new AtomicReference<>("requires_payment_method");
        var posts = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/payment_intents/pi_99395", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                posts.incrementAndGet(); amount.set(2501); providerStatus.set("succeeded");
            }
            byte[] body = ("{\"id\":\"pi_99395\",\"object\":\"payment_intent\",\"livemode\":false,\"currency\":\"usd\",\"amount\":" + amount.get() + ",\"status\":\"" + providerStatus.get() + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        String base = Stripe.getApiBase(), key = Stripe.apiKey;
        server.start();
        try {
            Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); Stripe.apiKey = "sk_test_fixture";
            assertThrows(PaymentProcessingException.class, () -> payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key"));
            clearInvocations(orderClient);
            assertThrows(PaymentProcessingException.class, () -> payments.syncPaymentStatusWithStripe(p.getId()));
            assertEquals(PaymentStatus.FAILED, repository.findById(p.getId()).orElseThrow().getStatus());
            revision(p.getId(), 0, 0);
            assertEquals("RESERVED", jdbc.queryForObject("select state from payment_retry_attempt where payment_id=?", String.class, p.getId()));
            verify(orderClient, never()).markOrderAsPaid(anyLong(), anyLong(), anyString());
            amount.set(2500);
            var response = payments.syncPaymentStatusWithStripe(p.getId());
            assertEquals(p.getId(), response.getId());
            assertEquals(PaymentStatus.SUCCEEDED, response.getStatus());
            revision(p.getId(), 1, 1);
            assertEquals(1, posts.get());
        } finally { Stripe.overrideApiBase(base); Stripe.apiKey = key; server.stop(0); }
    }

    @Test void publicConfirmUsesRealOwnerCheckAndConfirmsOwnersPayment() throws Exception {
        var p = payment(99383, "STRIPE", PaymentStatus.PENDING);
        when(factory.getGateway("STRIPE")).thenReturn(stripe);
        when(stripe.confirmPaymentIntent("pi_99383", "pm_retry"))
                .thenReturn(StripePaymentResponse.builder().paymentIntentId("pi_99383").status("succeeded").build());
        var mvc = MockMvcBuilders.standaloneSetup(new PaymentController(payments))
                .setControllerAdvice(new PaymentExceptionHandler()).build();
        mvc.perform(post("/payments/{paymentId}/confirm", p.getId()).param("paymentMethodId", "pm_retry")
                        .header("X-User-Id", "other").header("X-User-Username", "other"))
                .andExpect(status().isForbidden());
        verify(stripe, never()).confirmPaymentIntent(anyString(), anyString());
        var result = mvc.perform(post("/payments/{paymentId}/confirm", p.getId()).param("paymentMethodId", "pm_retry")
                        .header("X-User-Id", "owner").header("X-User-Username", "owner"))
                .andExpect(status().isOk()).andReturn();
        var data = new ObjectMapper().readTree(result.getResponse().getContentAsString()).path("data");
        assertEquals(p.getId().longValue(), data.path("id").asLong());
        assertEquals("pi_99383", data.path("stripePaymentIntentId").asText());
        assertEquals("SUCCEEDED", data.path("status").asText());
        assertEquals(1, repository.findByOrderId(p.getOrderId()).size());
        verify(stripe, times(1)).confirmPaymentIntent("pi_99383", "pm_retry");
    }

    @Test void publicConfirmWithoutTrustedUserContextReturnsUnauthorized() throws Exception {
        var p = payment(99384, "STRIPE", PaymentStatus.PENDING);
        var mvc = MockMvcBuilders.standaloneSetup(new PaymentController(payments))
                .setControllerAdvice(new PaymentExceptionHandler()).build();
        mvc.perform(post("/payments/{paymentId}/confirm", p.getId()).param("paymentMethodId", "pm_retry"))
                .andExpect(status().isUnauthorized());
        assertEquals(PaymentStatus.PENDING, repository.findById(p.getId()).orElseThrow().getStatus());
        verifyNoInteractions(stripe, factory);
    }
}
