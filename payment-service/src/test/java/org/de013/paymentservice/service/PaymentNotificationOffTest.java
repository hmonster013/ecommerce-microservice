package org.de013.paymentservice.service;

import java.math.BigDecimal;
import org.de013.paymentservice.dto.payment.ProcessPaymentRequest;
import org.de013.paymentservice.dto.stripe.StripePaymentRequest;
import org.de013.paymentservice.dto.stripe.StripePaymentResponse;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.enums.Currency;
import org.de013.paymentservice.entity.enums.PaymentMethodType;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.cloud.bus.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect", "spring.flyway.enabled=true",
        "payment.events.enabled=false", "payment.notifications.events.enabled=false", "payment.gateways.stripe.checkout-return-url=http://localhost:4200/checkout/result"})
@ActiveProfiles("test")
@org.testcontainers.junit.jupiter.Testcontainers
class PaymentNotificationOffTest {
    @org.testcontainers.junit.jupiter.Container static final org.testcontainers.containers.PostgreSQLContainer<?> DB = new org.testcontainers.containers.PostgreSQLContainer<>("postgres:17-alpine");
    @org.springframework.test.context.DynamicPropertySource static void database(org.springframework.test.context.DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", DB::getJdbcUrl); p.add("spring.datasource.username", DB::getUsername);
        p.add("spring.datasource.password", DB::getPassword); p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
    @org.springframework.beans.factory.annotation.Autowired PaymentService payments;
    @org.springframework.beans.factory.annotation.Autowired org.de013.paymentservice.repository.PaymentRepository repository;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired org.de013.paymentservice.config.PaymentGatewayConfig gatewayConfig;
    @org.springframework.beans.factory.annotation.Autowired WebhookService webhook;
    @org.springframework.beans.factory.annotation.Autowired VnpayIpnService ipn;
    @org.springframework.boot.test.mock.mockito.MockBean org.de013.paymentservice.client.HostedOrderClient orders;
    @org.springframework.boot.test.mock.mockito.MockBean org.de013.paymentservice.client.OrderServiceClient orderClient;
    @org.springframework.boot.test.mock.mockito.MockBean org.de013.paymentservice.client.NotificationServiceClient notifications;
    @org.springframework.boot.test.mock.mockito.MockBean org.de013.paymentservice.client.UserServiceClient users;
    @org.springframework.boot.test.mock.mockito.MockBean org.de013.paymentservice.gateway.PaymentGatewayFactory factory;
    @org.springframework.boot.test.mock.mockito.MockBean org.de013.paymentservice.gateway.stripe.StripePaymentGateway stripe;
    @org.springframework.boot.test.mock.mockito.MockBean org.de013.paymentservice.gateway.vnpay.VnpayPaymentGateway vnpay;
    @org.springframework.boot.test.mock.mockito.MockBean org.de013.paymentservice.service.validation.PaymentRequestValidator validator;

    private Payment payment(long id, PaymentStatus status) {
        return repository.saveAndFlush(Payment.builder().orderId(id).userId("owner").paymentNumber("TEST-" + id)
                .amount(new BigDecimal("25.00")).currency(Currency.USD).method(PaymentMethodType.CARD).status(status)
                .gatewayName("STRIPE").stripePaymentIntentId("pi_" + id).receiptEmail("fixture@example.test").build());
    }
    private StripePaymentResponse success(long id) { return StripePaymentResponse.builder().paymentIntentId("pi_" + id).status("succeeded").build(); }
    private void persisted(Payment p) {
        assertEquals(PaymentStatus.SUCCEEDED, repository.findById(p.getId()).orElseThrow().getStatus());
        assertEquals(0, jdbc.queryForObject("select count(*) from payment_notification_outbox where payment_id=?", Integer.class, p.getId()));
    }
    @Test void legacyEntryPointsKeepNotifyFlagSemantics() throws Exception {
        when(factory.getGateway("STRIPE")).thenReturn(stripe);
        when(stripe.mapToGatewayPaymentRequest(any())).thenReturn(StripePaymentRequest.builder().build());
        when(stripe.createPaymentIntent(any())).thenReturn(success(99630));
        payments.processPayment(ProcessPaymentRequest.builder().orderId(99630L).userId("owner").amount(new BigDecimal("25.00"))
                .currency(Currency.USD).paymentMethodType(PaymentMethodType.CARD).provider("STRIPE").receiptEmail("fixture@example.test").build());
        persisted(repository.findByOrderId(99630L).get(0));
        verify(notifications, times(1)).sendEmail(anyMap());
        var capture = payment(99631, PaymentStatus.REQUIRES_CONFIRMATION);
        when(stripe.capturePayment(eq("pi_99631"), any())).thenReturn(success(99631));
        payments.capturePayment(capture.getId(), null); persisted(capture);
        verify(notifications, times(2)).sendEmail(anyMap());
        var sync = payment(99632, PaymentStatus.PENDING);
        when(stripe.getPaymentIntent("pi_99632")).thenReturn(success(99632));
        payments.syncPaymentStatusWithStripe(sync.getId()); persisted(sync);
        verify(notifications, times(3)).sendEmail(anyMap());
        var confirm = payment(99633, PaymentStatus.PENDING);
        when(stripe.confirmPaymentIntent("pi_99633", "pm_test")).thenReturn(success(99633));
        payments.confirmPayment(confirm.getId(), "pm_test"); persisted(confirm);
        verify(notifications, times(3)).sendEmail(anyMap());
        var captureArgs = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(notifications, times(3)).sendEmail(captureArgs.capture());
        for (int i = 0; i < 3; i++) {
            var message = captureArgs.getAllValues().get(i);
            assertEquals(java.util.Set.of("userId", "to", "subject", "message"), message.keySet());
            assertEquals("owner", message.get("userId")); assertEquals("fixture@example.test", message.get("to"));
            assertEquals("Thanh toán thành công cho Đơn hàng #" + (99630 + i), message.get("subject"));
            assertTrue(((String) message.get("message")).contains("25.00 USD"));
        }
        verifyNoInteractions(users);
    }

    @Test void signedWebhookAndVnpayUseLegacyNotificationAndUserFallback() throws Exception {
        var user = new org.de013.paymentservice.dto.external.UserDto(); user.setEmail("fallback@example.test");
        when(users.getUserById("owner")).thenReturn(org.springframework.http.ResponseEntity.ok(org.de013.common.dto.ApiResult.success(user)));
        var stripePayment = payment(99635, PaymentStatus.PENDING); stripePayment.setReceiptEmail(null); repository.saveAndFlush(stripePayment);
        String body = "{\"id\":\"evt_99635\",\"object\":\"event\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"object\":\"payment_intent\",\"id\":\"pi_99635\",\"status\":\"succeeded\"}}}";
        long time = java.time.Instant.now().getEpochSecond();
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(webhook.getWebhookSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = java.util.HexFormat.of().formatHex(mac.doFinal((time + "." + body).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        webhook.processStripeWebhook(body, "t=" + time + ",v1=" + signature); persisted(stripePayment);
        var vn = payment(99636, PaymentStatus.PENDING); vn.setGatewayName("VNPAY"); vn.setStripePaymentIntentId(null);
        vn.setProviderAmountVnd(625000L); vn.setProviderCurrency("VND"); vn.setReceiptEmail(null); repository.saveAndFlush(vn);
        var cfg = gatewayConfig.getGateways().getVnpay(); var real = new org.de013.paymentservice.gateway.vnpay.VnpayPaymentGateway(gatewayConfig);
        when(vnpay.verifyCallback(anyMap())).thenAnswer(i -> real.verifyCallback(i.getArgument(0)));
        var fields = new java.util.TreeMap<String, String>();
        fields.put("vnp_TmnCode", cfg.getTmnCode()); fields.put("vnp_TxnRef", vn.getPaymentNumber()); fields.put("vnp_Amount", "62500000");
        fields.put("vnp_ResponseCode", "00"); fields.put("vnp_TransactionStatus", "00");
        fields.put("vnp_SecureHash", org.de013.paymentservice.gateway.vnpay.VnpayUtils.hmacSHA512(cfg.getHashSecret(), org.de013.paymentservice.gateway.vnpay.VnpayUtils.buildHashData(fields)));
        assertEquals("00", ipn.process(fields).get("RspCode")); persisted(vn);
        verify(users, times(2)).getUserById("owner");
        var args = org.mockito.ArgumentCaptor.forClass(java.util.Map.class); verify(notifications, times(2)).sendEmail(args.capture());
        for (int i = 0; i < 2; i++) {
            var message = args.getAllValues().get(i);
            assertEquals(java.util.Set.of("userId", "to", "subject", "message"), message.keySet());
            assertEquals("owner", message.get("userId")); assertEquals("fallback@example.test", message.get("to"));
            assertEquals("Thanh toán thành công cho Đơn hàng #" + (99635 + i), message.get("subject"));
            if (i == 0) assertTrue(((String) message.get("message")).contains("25.00 USD"));
            else assertEquals("Thanh toán thành công cho đơn hàng #99636", message.get("message"));
        }
    }

    @Test void retryConfirmAndReadBackDoNotSendLegacyEmail() throws Exception { retry(false); }
    @Test void retryConfirmSuccessDoesNotSendLegacyEmail() throws Exception { retry(true); }

    private void retry(boolean immediate) throws Exception {
        long orderId = immediate ? 99637 : 99634;
        var p = payment(orderId, PaymentStatus.FAILED);
        p.setStripeIntentCreatedByService(true); p.setStripeIntentLivemode(false); repository.saveAndFlush(p);
        var order = new org.de013.paymentservice.dto.external.OrderDto();
        order.setId(p.getOrderId()); order.setUserId("owner"); order.setStatus("PENDING");
        order.setInventoryReady(true); order.setPaymentRetryEligible(true); order.setCurrency("USD");
        var total = new org.de013.paymentservice.dto.external.OrderDto.MoneyDto(); total.setAmount(p.getAmount()); total.setCurrency("USD"); order.setTotalAmount(total);
        when(orderClient.getOrderById(p.getOrderId())).thenReturn(org.springframework.http.ResponseEntity.ok(order));
        when(stripe.getSupportedCurrencies()).thenCallRealMethod();
        when(factory.getGateway("STRIPE")).thenReturn(new org.de013.paymentservice.gateway.stripe.StripePaymentGateway(gatewayConfig, null, null, null));
        var posts = new java.util.concurrent.atomic.AtomicInteger(); var gets = new java.util.concurrent.atomic.AtomicInteger();
        var providerKey = new java.util.concurrent.atomic.AtomicReference<String>();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/payment_intents/pi_" + orderId, exchange -> {
            boolean post = "POST".equals(exchange.getRequestMethod()) && exchange.getRequestURI().getPath().equals("/v1/payment_intents/pi_" + orderId + "/confirm");
            boolean get = "GET".equals(exchange.getRequestMethod()) && exchange.getRequestURI().getPath().equals("/v1/payment_intents/pi_" + orderId);
            if (!post && !get) { exchange.sendResponseHeaders(404, -1); exchange.close(); return; }
            if (post) { posts.incrementAndGet(); providerKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key")); } else gets.incrementAndGet();
            String status = post ? (immediate ? "succeeded" : "processing") : posts.get() == 0 ? "requires_payment_method" : "succeeded";
            byte[] body = ("{\"id\":\"pi_" + orderId + "\",\"object\":\"payment_intent\",\"livemode\":false,\"currency\":\"usd\",\"amount\":2500,\"status\":\"" + status + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        String base = com.stripe.Stripe.getApiBase(), key = com.stripe.Stripe.apiKey; server.start();
        try {
            com.stripe.Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); com.stripe.Stripe.apiKey = "sk_test_fixture";
            assertEquals(immediate ? PaymentStatus.SUCCEEDED : PaymentStatus.PROCESSING, payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key").getStatus());
            assertEquals(immediate ? "COMPLETED" : "RESERVED", jdbc.queryForObject("select state from payment_retry_attempt where payment_id=?", String.class, p.getId()));
            assertNotNull(providerKey.get());
            assertEquals(providerKey.get(), jdbc.queryForObject("select provider_key::text from payment_retry_attempt where payment_id=? and idempotency_key='key'", String.class, p.getId()));
            assertEquals(1, jdbc.queryForObject("select count(*) from payment_retry_attempt where payment_id=? and payment_method_id='pm_retry' and amount=25.00 and currency='USD'", Integer.class, p.getId()));
            verifyNoInteractions(notifications, users);
            int before = gets.get();
            assertEquals(PaymentStatus.SUCCEEDED, payments.confirmPaymentForOwner(p.getId(), "pm_retry", "owner", "key").getStatus());
            assertEquals(before + (immediate ? 0 : 1), gets.get()); assertEquals(1, posts.get()); persisted(p);
            verifyNoInteractions(notifications, users);
        } finally { com.stripe.Stripe.overrideApiBase(base); com.stripe.Stripe.apiKey = key; server.stop(0); }
    }
}
