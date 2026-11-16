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

}
