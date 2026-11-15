package org.de013.paymentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
        assertEquals("VNPAY", new ObjectMapper().readTree(original).path("provider").asText());
        assertEquals("02", ipn.process(fields).get("RspCode")); assertEquals(original, event(p.getId()));
        var heldVnpay = payment(99106, "VNPAY", PaymentStatus.PENDING);
        new org.springframework.transaction.support.TransactionTemplate(tx).executeWithoutResult(s -> guard.hold(99106L));
        fields.put("vnp_TxnRef", heldVnpay.getPaymentNumber()); fields.remove("vnp_SecureHash");
        fields.put("vnp_SecureHash", org.de013.paymentservice.gateway.vnpay.VnpayUtils.hmacSHA512(cfg.getHashSecret(), org.de013.paymentservice.gateway.vnpay.VnpayUtils.buildHashData(fields)));
        assertEquals("00", ipn.process(fields).get("RspCode"));
        assertEquals(PaymentStatus.SUCCEEDED, repository.findById(heldVnpay.getId()).orElseThrow().getStatus()); assertEquals(0, count(heldVnpay.getId()));
        var released = payment(99104, "STRIPE", PaymentStatus.PENDING);
        send(99104, "canceled"); send(99104, "succeeded");
        assertEquals(PaymentStatus.SUCCEEDED, repository.findById(released.getId()).orElseThrow().getStatus());
        assertTrue(jdbc.queryForObject("select reconciliation_hold from payment_order_guard where order_id=?", Boolean.class, 99104L));
        assertEquals(0, count(released.getId()));
        var held = hostedStore.reserve(99105L, "owner", new BigDecimal("25.00"), "https://fixture.test/checkout/result");
        new org.springframework.transaction.support.TransactionTemplate(tx).executeWithoutResult(s -> guard.hold(99105L));
        hostedStore.record(99105L, 0, "cs_99105", "PAID", "pi_99105");
        assertEquals(PaymentStatus.SUCCEEDED, repository.findById(held.getPaymentId()).orElseThrow().getStatus());
        assertEquals(0, count(held.getPaymentId()));
        verify(orderClient, never()).markOrderAsPaid(anyLong(), anyLong(), anyString());
    }
    @Test void hostedRecordPersistsExactEventAndRepeatedReadKeepsItStable() throws Exception {
        var checkout = hostedStore.reserve(99001L, "owner", new BigDecimal("25.00"), "https://fixture.test/checkout/result");
        hostedStore.record(99001L, 0, "cs_99001", "PAID", "pi_99001");
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
}
