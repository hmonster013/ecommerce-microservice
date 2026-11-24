package org.de013.paymentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.cloud.bus.enabled=false", "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect", "spring.flyway.enabled=true", "payment.events.enabled=true", "payment.events.relay-delay-ms=3600000", "payment.notifications.events.enabled=true", "payment.notifications.events.relay-delay-ms=3600000", "payment.gateways.stripe.checkout-return-url=http://localhost:4200/checkout/result"})
@ActiveProfiles("test")
@org.testcontainers.junit.jupiter.Testcontainers
class PaymentNotificationRetryTest {
    @org.testcontainers.junit.jupiter.Container static final org.testcontainers.containers.PostgreSQLContainer<?> DB = new org.testcontainers.containers.PostgreSQLContainer<>("postgres:17-alpine");
    @org.springframework.test.context.DynamicPropertySource static void database(org.springframework.test.context.DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", DB::getJdbcUrl); p.add("spring.datasource.username", DB::getUsername); p.add("spring.datasource.password", DB::getPassword); p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver"); p.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
    @org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired org.de013.paymentservice.repository.PaymentRepository repository;
    @org.springframework.beans.factory.annotation.Autowired PaymentService payments;
    @org.springframework.beans.factory.annotation.Autowired WebhookService webhook;
    @org.springframework.beans.factory.annotation.Autowired PaymentRetryAttempts retryAttempts;
    @org.springframework.beans.factory.annotation.Autowired org.de013.paymentservice.config.PaymentGatewayConfig gatewayConfig;
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.de013.paymentservice.client.HostedOrderClient orders;
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.de013.paymentservice.client.OrderServiceClient orderClient;
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.de013.paymentservice.client.NotificationServiceClient notifications;
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.de013.paymentservice.client.UserServiceClient users;
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.de013.paymentservice.gateway.PaymentGatewayFactory factory;
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.de013.paymentservice.gateway.stripe.StripePaymentGateway stripe;
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.de013.paymentservice.gateway.vnpay.VnpayPaymentGateway vnpay;
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.de013.paymentservice.service.validation.PaymentRequestValidator validator;
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

    private String payload(long id) {
        return jdbc.queryForObject("select payload from payment_notification_outbox where payment_id=?", String.class, id);
    }
    private int count(long id) {
        return jdbc.queryForObject("select count(*) from payment_notification_outbox where payment_id=?", Integer.class, id);
    }
    private void contract(org.de013.paymentservice.entity.Payment payment) throws Exception {
        assertEquals(PaymentStatus.SUCCEEDED, repository.findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(1, count(payment.getId()));
        assertEquals("PENDING", jdbc.queryForObject("select state from payment_notification_outbox where payment_id=?", String.class, payment.getId()));
        var json = new ObjectMapper().readTree(payload(payment.getId()));
        assertEquals(12, json.size());
        java.util.UUID.fromString(json.path("eventId").asText());
        java.time.Instant.parse(json.path("occurredAt").asText());
        assertEquals("PaymentEmailRequested", json.path("eventType").asText());
        assertEquals(1, json.path("schemaVersion").asInt());
        assertEquals(payment.getId().longValue(), json.path("paymentId").asLong());
        assertEquals(payment.getOrderId().longValue(), json.path("orderId").asLong());
        assertEquals(payment.getPaymentNumber(), json.path("paymentNumber").asText());
        assertEquals("owner", json.path("userId").asText());
        assertEquals("EMAIL", json.path("channel").asText());
        assertEquals("fixture@example.test", json.path("recipient").asText());
        assertFalse(json.path("subject").asText().isBlank());
        assertTrue(json.path("content").asText().contains("25.00 USD"));
    }

    @Test void retryNotificationFailureRecoversByProviderReadBack() throws Exception { retry(true); }
    @Test void retryConfirmSuccessCommitsSnapshot() throws Exception { retry(false); }

    private void retry(boolean fail) throws Exception {
        long orderId = fail ? 99620 : 99624;
        var p = payment(orderId, "STRIPE", PaymentStatus.FAILED);
        p.setStripeIntentCreatedByService(true); p.setStripeIntentLivemode(false); repository.saveAndFlush(p);
        var order = new org.de013.paymentservice.dto.external.OrderDto();
        order.setId(p.getOrderId()); order.setUserId("owner"); order.setStatus("PENDING");
        order.setInventoryReady(true); order.setPaymentRetryEligible(true); order.setCurrency("USD");
        var total = new org.de013.paymentservice.dto.external.OrderDto.MoneyDto();
        total.setAmount(p.getAmount()); total.setCurrency("USD"); order.setTotalAmount(total);
        when(orderClient.getOrderById(p.getOrderId())).thenReturn(org.springframework.http.ResponseEntity.ok(order));
        when(stripe.getSupportedCurrencies()).thenCallRealMethod();
        when(factory.getGateway("STRIPE")).thenReturn(new org.de013.paymentservice.gateway.stripe.StripePaymentGateway(gatewayConfig, null, null, null));
        var posts = new java.util.concurrent.atomic.AtomicInteger();
        var gets = new java.util.concurrent.atomic.AtomicInteger();
        var providerKey = new java.util.concurrent.atomic.AtomicReference<String>();
        var providerStatus = new java.util.concurrent.atomic.AtomicReference<>("requires_payment_method");
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/payment_intents/pi_" + orderId, exchange -> {
            if ("POST".equals(exchange.getRequestMethod()) && exchange.getRequestURI().getPath().equals("/v1/payment_intents/pi_" + orderId + "/confirm")) {
                posts.incrementAndGet(); providerKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key")); providerStatus.set("succeeded");
            } else if ("GET".equals(exchange.getRequestMethod()) && exchange.getRequestURI().getPath().equals("/v1/payment_intents/pi_" + orderId)) gets.incrementAndGet();
            else { exchange.sendResponseHeaders(404, -1); exchange.close(); return; }
            byte[] body = ("{\"id\":\"pi_" + orderId + "\",\"object\":\"payment_intent\",\"livemode\":false,\"currency\":\"usd\",\"amount\":2500,\"status\":\"" + providerStatus.get() + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        String base = com.stripe.Stripe.getApiBase(), key = com.stripe.Stripe.apiKey;
        server.start();
        try {
            com.stripe.Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); com.stripe.Stripe.apiKey = "sk_test_fixture";
            if (fail) {
            jdbc.execute("create function reject_notification_99620() returns trigger language plpgsql as $$ begin if new.payment_id=" + p.getId() + " then raise exception 'injected'; end if; return new; end $$");
            try {
                jdbc.execute("create trigger reject_notification_99620 before insert on payment_notification_outbox for each row execute function reject_notification_99620()");
                var error = assertThrows(RuntimeException.class, () -> payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key"));
                assertTrue(java.util.stream.Stream.iterate((Throwable) error, java.util.Objects::nonNull, Throwable::getCause).anyMatch(e -> e instanceof org.postgresql.util.PSQLException && e.getMessage().contains("injected")));
            } finally {
                jdbc.execute("drop trigger if exists reject_notification_99620 on payment_notification_outbox");
                jdbc.execute("drop function if exists reject_notification_99620()");
            }
            } else {
                assertEquals(PaymentStatus.SUCCEEDED, payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key").getStatus());
            }
            assertEquals(1, posts.get()); assertNotNull(providerKey.get());
            assertEquals(providerKey.get(), jdbc.queryForObject("select provider_key::text from payment_retry_attempt where payment_id=? and idempotency_key='key'", String.class, p.getId()));
            var attempt = retryAttempts.existing(p.getId(), "key", "pm_retry", p.getAmount(), "USD");
            assertEquals(fail ? "RESERVED" : "COMPLETED", attempt.state());
            assertEquals(1, jdbc.queryForObject("select count(*) from payment_retry_attempt where payment_id=?", Integer.class, p.getId()));
            assertEquals(false, jdbc.queryForObject("select reconciliation_hold or close_operation_id is not null from payment_order_guard where order_id=?", Boolean.class, p.getOrderId()));
            if (fail) {
                assertEquals(PaymentStatus.FAILED, repository.findById(p.getId()).orElseThrow().getStatus());
                assertEquals(0, repository.findById(p.getId()).orElseThrow().getRevision());
                assertEquals(0, count(p.getId()));
                assertEquals(0, jdbc.queryForObject("select count(*) from payment_outbox where payment_id=?", Integer.class, p.getId()));
                assertEquals(0, jdbc.queryForObject("select count(*) from payment_transactions where payment_id=?", Integer.class, p.getId()));
                assertTrue(retryAttempts.active(p.getId()));
                int before = gets.get();
                assertEquals(PaymentStatus.SUCCEEDED, payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key").getStatus());
                assertEquals(before + 1, gets.get()); assertEquals(1, posts.get());
            }
            var after = retryAttempts.existing(p.getId(), "key", "pm_retry", p.getAmount(), "USD");
            assertEquals(attempt.id(), after.id()); assertEquals(attempt.providerKey(), after.providerKey());
            assertEquals(fail ? "RESERVED" : "COMPLETED", after.state());
            assertEquals(fail, retryAttempts.active(p.getId()));
            assertEquals(false, jdbc.queryForObject("select reconciliation_hold or close_operation_id is not null from payment_order_guard where order_id=?", Boolean.class, p.getOrderId()));
            assertEquals(1, repository.findById(p.getId()).orElseThrow().getRevision());
            assertEquals(1, jdbc.queryForObject("select count(*) from payment_outbox where payment_id=?", Integer.class, p.getId()));
            assertEquals(PaymentStatus.SUCCEEDED, repository.findById(p.getId()).orElseThrow().getStatus());
            assertEquals(1, jdbc.queryForObject("select count(*) from payment_transactions where payment_id=?", Integer.class, p.getId()));
            contract(p); assertEquals(1, count(p.getId())); String snapshot = payload(p.getId());
            String financialSnapshot = jdbc.queryForObject("select payload from payment_outbox where payment_id=?", String.class, p.getId());
            payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key");
            send(orderId, "succeeded");
            assertEquals(1, posts.get()); assertEquals(1, count(p.getId())); assertEquals(snapshot, payload(p.getId()));
            assertEquals(1, jdbc.queryForObject("select count(*) from payment_outbox where payment_id=?", Integer.class, p.getId()));
            assertEquals(financialSnapshot, jdbc.queryForObject("select payload from payment_outbox where payment_id=?", String.class, p.getId()));
            assertEquals(1, jdbc.queryForObject("select count(*) from payment_transactions where payment_id=?", Integer.class, p.getId()));
            verifyNoInteractions(notifications, users);
        } finally { com.stripe.Stripe.overrideApiBase(base); com.stripe.Stripe.apiKey = key; server.stop(0); }
    }

}
