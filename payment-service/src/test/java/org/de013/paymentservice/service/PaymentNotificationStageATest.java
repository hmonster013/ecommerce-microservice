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
        "payment.events.enabled=false", "payment.notifications.events.enabled=true", "payment.notifications.events.relay-delay-ms=3600000", "payment.gateways.stripe.checkout-return-url=http://localhost:4200/checkout/result"})
@ActiveProfiles("test")
@org.testcontainers.junit.jupiter.Testcontainers
class PaymentNotificationStageATest {
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
    @org.springframework.beans.factory.annotation.Autowired PaymentRetryAttempts retryAttempts;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.transaction.PlatformTransactionManager tx;
    @org.springframework.beans.factory.annotation.Autowired jakarta.validation.Validator validatorBean;
    @org.springframework.beans.factory.annotation.Autowired PaymentNotificationOutbox outbox;

    @Test void traceIsPersistedAndRolledBackWithNotification() {
        var p = payment(99881, "STRIPE", PaymentStatus.PENDING);
        var span = io.opentelemetry.api.trace.Span.wrap(io.opentelemetry.api.trace.SpanContext.create(
                "0123456789abcdef0123456789abcdef", "0123456789abcdef", io.opentelemetry.api.trace.TraceFlags.getSampled(), io.opentelemetry.api.trace.TraceState.getDefault()));
        try (var scope = span.makeCurrent()) {
            new org.springframework.transaction.support.TransactionTemplate(tx).executeWithoutResult(s -> { outbox.enqueue(p); s.setRollbackOnly(); });
            assertEquals(0, count(p.getId()));
            new org.springframework.transaction.support.TransactionTemplate(tx).executeWithoutResult(s -> outbox.enqueue(p));
        }
        assertEquals("00-0123456789abcdef0123456789abcdef-0123456789abcdef-01",
                jdbc.queryForObject("select traceparent from payment_notification_outbox where payment_id=?", String.class, p.getId()));
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

    @Test void apiWebhookAndHistory() throws Exception {
        when(factory.getGateway("STRIPE")).thenReturn(stripe);
        var api = payment(99601, "STRIPE", PaymentStatus.PENDING);
        when(stripe.getPaymentIntent("pi_99601")).thenReturn(org.de013.paymentservice.dto.stripe.StripePaymentResponse.builder().paymentIntentId("pi_99601").status("succeeded").build());
        payments.syncPaymentStatusWithStripe(api.getId());
        contract(api); String snapshot = payload(api.getId());
        send(99601, "succeeded"); assertEquals(snapshot, payload(api.getId())); assertEquals(1, count(api.getId()));
        var webhookOnly = payment(99602, "STRIPE", PaymentStatus.PENDING);
        send(99602, "succeeded"); contract(webhookOnly);
        var historical = payment(99603, "STRIPE", PaymentStatus.SUCCEEDED);
        send(99603, "succeeded"); assertEquals(0, count(historical.getId()));
        verifyNoInteractions(notifications, users);
        verify(orderClient, atLeastOnce()).markOrderAsPaid(anyLong(), anyLong(), anyString());
    }

    @Test void confirmSuccessThenWebhookRequestsEmailExactlyOnce() throws Exception {
        var p = payment(99609, "STRIPE", PaymentStatus.PENDING);
        when(factory.getGateway("STRIPE")).thenReturn(stripe);
        when(stripe.confirmPaymentIntent("pi_99609", "pm_fixture"))
                .thenReturn(org.de013.paymentservice.dto.stripe.StripePaymentResponse.builder()
                        .paymentIntentId("pi_99609").status("succeeded").build());
        payments.confirmPayment(p.getId(), "pm_fixture");
        contract(p);
        String snapshot = payload(p.getId());
        send(99609, "succeeded");
        assertEquals(1, count(p.getId()));
        assertEquals(snapshot, payload(p.getId()));
        verifyNoInteractions(notifications, users);
    }

    @Test void processAndCaptureRequestSnapshots() throws Exception {
        when(factory.getGateway("STRIPE")).thenReturn(stripe);
        when(stripe.mapToGatewayPaymentRequest(any())).thenReturn(org.de013.paymentservice.dto.stripe.StripePaymentRequest.builder().build());
        when(stripe.createPaymentIntent(any())).thenReturn(org.de013.paymentservice.dto.stripe.StripePaymentResponse.builder().paymentIntentId("pi_99621").status("succeeded").build());
        payments.processPayment(org.de013.paymentservice.dto.payment.ProcessPaymentRequest.builder().orderId(99621L).userId("owner").amount(new BigDecimal("25.00"))
                .currency(org.de013.paymentservice.entity.enums.Currency.USD).paymentMethodType(org.de013.paymentservice.entity.enums.PaymentMethodType.CARD).provider("STRIPE").receiptEmail("fixture@example.test").build());
        var created = repository.findByOrderId(99621L).get(0);
        assertEquals(PaymentStatus.SUCCEEDED, created.getStatus()); contract(created); assertEquals(1, count(created.getId()));
        var capture = payment(99622, "STRIPE", PaymentStatus.REQUIRES_CONFIRMATION);
        when(stripe.capturePayment(eq("pi_99622"), any())).thenReturn(org.de013.paymentservice.dto.stripe.StripePaymentResponse.builder().paymentIntentId("pi_99622").status("succeeded").build());
        payments.capturePayment(capture.getId(), null);
        assertEquals(PaymentStatus.SUCCEEDED, repository.findById(capture.getId()).orElseThrow().getStatus()); contract(capture); assertEquals(1, count(capture.getId()));
        assertEquals("PENDING", jdbc.queryForObject("select state from payment_notification_outbox where payment_id=?", String.class, capture.getId()));
        verifyNoInteractions(notifications, users);
    }

    @Test void signedVnpayAndMissingRecipientAndHold() throws Exception {
        var cfg = gatewayConfig.getGateways().getVnpay();
        var real = new org.de013.paymentservice.gateway.vnpay.VnpayPaymentGateway(gatewayConfig);
        when(vnpayGateway.verifyCallback(anyMap())).thenAnswer(i -> real.verifyCallback(i.getArgument(0)));
        var p = payment(99604, "VNPAY", PaymentStatus.PENDING);
        var fields = new java.util.TreeMap<String, String>();
        fields.put("vnp_TmnCode", cfg.getTmnCode()); fields.put("vnp_TxnRef", p.getPaymentNumber());
        fields.put("vnp_Amount", "62500000"); fields.put("vnp_ResponseCode", "00"); fields.put("vnp_TransactionStatus", "00");
        fields.put("vnp_SecureHash", org.de013.paymentservice.gateway.vnpay.VnpayUtils.hmacSHA512(cfg.getHashSecret(), org.de013.paymentservice.gateway.vnpay.VnpayUtils.buildHashData(fields)));
        assertEquals("00", ipn.process(fields).get("RspCode")); contract(p); String snapshot = payload(p.getId());
        assertEquals("02", ipn.process(fields).get("RspCode")); assertEquals(snapshot, payload(p.getId()));
        var missing = payment(99605, "STRIPE", PaymentStatus.PENDING);
        missing.setReceiptEmail(null); repository.saveAndFlush(missing); send(99605, "succeeded");
        assertEquals("RECONCILIATION", jdbc.queryForObject("select state from payment_notification_outbox where payment_id=?", String.class, missing.getId()));
        var invalid = payment(99623, "STRIPE", PaymentStatus.PENDING);
        invalid.setReceiptEmail("not-an-email"); repository.saveAndFlush(invalid); send(99623, "succeeded");
        assertEquals(PaymentStatus.SUCCEEDED, repository.findById(invalid.getId()).orElseThrow().getStatus());
        assertEquals(1, count(invalid.getId()));
        assertEquals("RECONCILIATION", jdbc.queryForObject("select state from payment_notification_outbox where payment_id=?", String.class, invalid.getId()));
        var held = payment(99606, "STRIPE", PaymentStatus.PENDING);
        new org.springframework.transaction.support.TransactionTemplate(tx).executeWithoutResult(s -> guard.hold(99606L));
        send(99606, "succeeded"); assertEquals(0, count(held.getId()));
        verifyNoInteractions(notifications, users);
    }

    @Test void insertionFailureRollsBackFinancialState() throws Exception {
        var p = payment(99607, "STRIPE", PaymentStatus.PENDING);
        jdbc.execute("create function fail_notification_99607() returns trigger language plpgsql as $$ begin if new.payment_id=" + p.getId() + " then raise exception 'injected'; end if; return new; end $$");
        jdbc.execute("create trigger fail_notification_99607 before insert on payment_notification_outbox for each row execute function fail_notification_99607()");
        try {
            assertThrows(Exception.class, () -> send(99607, "succeeded"));
            assertEquals(PaymentStatus.PENDING, repository.findById(p.getId()).orElseThrow().getStatus());
            assertEquals(0, count(p.getId()));
        } finally {
            jdbc.execute("drop trigger fail_notification_99607 on payment_notification_outbox");
            jdbc.execute("drop function fail_notification_99607()");
        }
    }

    @Test void hostedCheckoutAndStripeWebhookNeverRequestEmail() throws Exception {
        var hosted = hostedStore.reserve(99608L, "owner", new BigDecimal("25.00"), "https://fixture.test/checkout/result");
        hostedStore.record(99608L, 0, "cs_99608", "PAID", "pi_99608");
        send(99608, "succeeded");
        assertEquals(0, count(hosted.getPaymentId()));
        verifyNoInteractions(notifications, users);
    }

    @Test void writerFlagsAreIndependentInAllCombinations() {
        int n = 0;
        for (boolean financial : new boolean[]{false, true}) for (boolean notification : new boolean[]{false, true}) {
            var p = payment(99700 + n++, "STRIPE", PaymentStatus.PENDING);
            var financialWriter = new PaymentOutbox(jdbc, new ObjectMapper(), financial);
            var notificationWriter = new PaymentNotificationOutbox(jdbc, new ObjectMapper(), validatorBean, notification);
            new org.springframework.transaction.support.TransactionTemplate(tx).executeWithoutResult(s -> {
                guard.lock(p.getOrderId());
                var managed = repository.findById(p.getId()).orElseThrow();
                financialWriter.transition(managed, PaymentStatus.SUCCEEDED, false);
                notificationWriter.enqueue(managed);
            });
            assertEquals(financial ? 1 : 0, jdbc.queryForObject("select count(*) from payment_outbox where payment_id=?", Integer.class, p.getId()));
            assertEquals(notification ? 1 : 0, count(p.getId()));
        }
    }

    @Test void invalidUserRequiresReconciliationAndConflictingIdentityFails() {
        var p = payment(99710, "STRIPE", PaymentStatus.PENDING);
        p.setUserId("x".repeat(256));
        var writer = new PaymentNotificationOutbox(jdbc, new ObjectMapper(), validatorBean, true);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(tx);
        transaction.executeWithoutResult(s -> writer.enqueue(p));
        assertEquals("RECONCILIATION", jdbc.queryForObject("select state from payment_notification_outbox where payment_id=?", String.class, p.getId()));
        p.setOrderId(99711L);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(s -> writer.enqueue(p)));
    }
}
