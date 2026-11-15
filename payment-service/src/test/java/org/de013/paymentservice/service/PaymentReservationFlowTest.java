package org.de013.paymentservice.service;

import org.de013.paymentservice.client.HostedOrderClient;
import org.de013.paymentservice.controller.HostedCheckoutController;
import org.de013.paymentservice.dto.external.OrderDto;
import org.de013.paymentservice.dto.payment.ProcessPaymentRequest;
import org.de013.paymentservice.dto.stripe.StripePaymentRequest;
import org.de013.paymentservice.dto.stripe.StripePaymentResponse;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.enums.Currency;
import org.de013.paymentservice.entity.enums.PaymentMethodType;
import org.de013.paymentservice.exception.PaymentExceptionHandler;
import org.de013.paymentservice.gateway.PaymentGatewayFactory;
import org.de013.paymentservice.gateway.stripe.StripeHostedGateway;
import org.de013.paymentservice.gateway.stripe.StripePaymentGateway;
import org.de013.paymentservice.gateway.vnpay.VnpayPaymentGateway;
import org.de013.paymentservice.repository.PaymentRepository;
import org.de013.paymentservice.service.validation.PaymentRequestValidator;
import org.de013.paymentservice.service.vnpay.VnpayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {"spring.cloud.bus.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect", "spring.flyway.enabled=true",
        "payment.gateways.stripe.checkout-return-url=http://localhost:4200/checkout/result"})
@ActiveProfiles("test")
class PaymentReservationFlowTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", DB::getJdbcUrl);
        p.add("spring.datasource.username", DB::getUsername);
        p.add("spring.datasource.password", DB::getPassword);
        p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
    @Autowired PaymentService payments;
    @Autowired VnpayService vnpay;
    @Autowired org.de013.paymentservice.config.PaymentGatewayConfig gatewayConfig;
    @Autowired VnpayIpnService ipn;
    @Autowired HostedCheckoutService hosted;
    @Autowired PaymentReservation reservation;
    @Autowired PaymentOrderGuard guard;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager jpa;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired WebhookService webhook;
    @Autowired org.de013.paymentservice.controller.WebhookController webhookController;
    @Autowired org.de013.paymentservice.repository.ProcessedStripeEventRepository events;
    @Autowired RefundService refunds;
    @Autowired org.de013.paymentservice.repository.RefundRepository refundRepository;
    @MockBean org.de013.paymentservice.client.OrderServiceClient orderClient;
    @MockBean org.de013.paymentservice.client.NotificationServiceClient notifications;
    @SpyBean PaymentRepository repository;
    @SpyBean HostedCheckoutStore hostedStore;
    @MockBean PaymentRequestValidator validator;
    @MockBean PaymentGatewayFactory factory;
    @MockBean StripePaymentGateway stripe;
    @MockBean StripeHostedGateway hostedStripe;
    @MockBean VnpayPaymentGateway vnpayGateway;
    @MockBean HostedOrderClient orders;

    @BeforeEach void setup() throws Exception {
        when(factory.getGateway("STRIPE")).thenReturn(stripe);
        when(stripe.mapToGatewayPaymentRequest(any())).thenReturn(StripePaymentRequest.builder().build());
        when(stripe.createPaymentIntent(any())).thenAnswer(invocation -> StripePaymentResponse.builder().paymentIntentId("pi_fixture_" + java.util.UUID.randomUUID()).status("processing").build());
        when(vnpayGateway.buildPaymentUrl(anyString(), anyLong(), anyString(), anyString(), anyString())).thenReturn("https://fixture.test");
    }
    private ProcessPaymentRequest request(long id, String provider) {
        return ProcessPaymentRequest.builder().orderId(id).userId("owner").amount(new BigDecimal("25.00"))
                .currency(Currency.USD).paymentMethodType(PaymentMethodType.CARD).provider(provider).build();
    }

    @Test void vnpaySnapshotCommittedBeforeSignedUrlAndSurvivesRateChange() {
        var cfg = gatewayConfig.getGateways().getVnpay();
        long originalRate = cfg.getVndRate();
        var realGateway = new VnpayPaymentGateway(gatewayConfig);
        try {
            when(vnpayGateway.verifyCallback(anyMap())).thenAnswer(i -> realGateway.verifyCallback(i.getArgument(0)));
            when(vnpayGateway.buildPaymentUrl(anyString(), anyLong(), anyString(), anyString(), anyString())).thenAnswer(i -> {
                String ref = i.getArgument(0);
                long amount = i.getArgument(1);
                try (var c = java.sql.DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
                     var s = c.prepareStatement("select provider_amount_vnd, provider_currency from payments where payment_number = ?")) {
                    s.setString(1, ref);
                    try (var rows = s.executeQuery()) {
                        assertTrue(rows.next()); assertEquals(amount, rows.getLong(1)); assertEquals("VND", rows.getString(2));
                    }
                }
                return realGateway.buildPaymentUrl(ref, amount, i.getArgument(2), i.getArgument(3), i.getArgument(4));
            });
            var result = vnpay.createCheckout(request(8991, "VNPAY"));
            var payment = repository.findByOrderId(8991L).get(0);
            long wire = Math.round(25.0 * originalRate) * 100;
            assertTrue(result.getRedirectUrl().contains("vnp_Amount=" + wire + "&"));
            assertThrows(IllegalArgumentException.class, () -> realGateway.buildPaymentUrl("ref", Long.MAX_VALUE / 100 + 1, "order", "127.0.0.1", "20260101000000"));
            cfg.setVndRate(originalRate + 1);
            var fields = new java.util.TreeMap<String, String>();
            fields.put("vnp_TmnCode", cfg.getTmnCode()); fields.put("vnp_TxnRef", payment.getPaymentNumber());
            fields.put("vnp_Amount", Long.toString(wire)); fields.put("vnp_ResponseCode", "00"); fields.put("vnp_TransactionStatus", "00");
            fields.put("vnp_SecureHash", org.de013.paymentservice.gateway.vnpay.VnpayUtils.hmacSHA512(cfg.getHashSecret(), org.de013.paymentservice.gateway.vnpay.VnpayUtils.buildHashData(fields)));
            assertEquals("00", ipn.process(fields).get("RspCode"));
            assertEquals(org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, repository.findByPaymentNumber(payment.getPaymentNumber()).orElseThrow().getStatus());
        } finally { cfg.setVndRate(originalRate); }
    }

    @Test void invalidVnpayConversionNeverReservesOrCallsGateway() {
        var cfg = gatewayConfig.getGateways().getVnpay();
        long rate = cfg.getVndRate();
        try {
            long id = 8992;
            for (var values : new String[][]{{"0", "25"}, {"-1", "25"}, {"1", "0.1"}, {"1", "1E+400"}, {"9223372036854775807", "25"}, {"1", "92233720368547759"}}) {
                cfg.setVndRate(Long.parseLong(values[0]));
                var req = request(id++, "VNPAY"); req.setAmount(new BigDecimal(values[1]));
                assertThrows(IllegalArgumentException.class, () -> vnpay.createCheckout(req));
                assertTrue(repository.findByOrderId(req.getOrderId()).isEmpty());
            }
            verify(vnpayGateway, never()).buildPaymentUrl(anyString(), anyLong(), anyString(), anyString(), anyString());
        } finally { cfg.setVndRate(rate); }
    }

    @Test void stripeAndVnpayRaceBeforeProvider() throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> { start.await(); try { payments.processPayment(request(8101, "STRIPE")); return 201; } catch (ResponseStatusException e) { return e.getStatusCode().value(); } });
            var b = pool.submit(() -> { org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(new org.springframework.mock.web.MockHttpServletRequest())); try { start.await(); try { vnpay.createCheckout(request(8101, "VNPAY")); return 201; } catch (ResponseStatusException e) { return e.getStatusCode().value(); } } finally { org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); } });
            start.countDown();
            var statuses = java.util.List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            assertTrue(statuses.containsAll(java.util.List.of(201, 409)), statuses.toString());
            assertEquals(1, mockingDetails(stripe).getInvocations().stream().filter(i -> i.getMethod().getName().equals("createPaymentIntent")).count()
                    + mockingDetails(vnpayGateway).getInvocations().stream().filter(i -> i.getMethod().getName().equals("buildPaymentUrl")).count());
            assertEquals(1, repository.findByOrderId(8101L).size());
        } finally { pool.shutdownNow(); }
    }

    @Test void providerSuccessThenOuterSaveFailsLeavesDurableReservation() throws Exception {
        doThrow(new IllegalStateException("simulated outer save failure")).when(repository).save(any(Payment.class));
        assertThrows(RuntimeException.class, () -> payments.processPayment(request(8102, "STRIPE")));
        assertEquals(1, repository.findByOrderId(8102L).size());
        assertThrows(ResponseStatusException.class, () -> payments.processPayment(request(8102, "STRIPE")));
        verify(stripe, times(1)).createPaymentIntent(any());
    }

    @Test void hostedIsBlockedByExistingVnpayReservation() throws Exception {
        vnpay.createCheckout(request(8103, "VNPAY"));
        var order = new OrderDto(); order.setId(8103L); order.setUserId("owner"); order.setStatus("PENDING");
        var money = new OrderDto.MoneyDto(); money.setAmount(new BigDecimal("25.00")); money.setCurrency("USD"); order.setTotalAmount(money);
        when(orders.read(8103L, "owner", "hosted-checkout")).thenReturn(ResponseEntity.ok(order));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> hosted.start(8103L, "owner")).getStatusCode().value());
        verify(hostedStripe, never()).create(any());
    }

    @Test void concurrentHostedStartsUseSingleProviderResource() throws Exception {
        var order = new OrderDto(); order.setId(8104L); order.setUserId("owner"); order.setStatus("PENDING");
        var money = new OrderDto.MoneyDto(); money.setAmount(new BigDecimal("25.00")); money.setCurrency("USD"); order.setTotalAmount(money);
        when(orders.read(8104L, "owner", "hosted-checkout")).thenReturn(ResponseEntity.ok(order));
        var bothChecked = new CountDownLatch(2);
        var conflicts = new java.util.concurrent.atomic.AtomicInteger();
        var target = org.springframework.test.util.AopTestUtils.<HostedCheckoutStore>getTargetObject(hostedStore);
        doAnswer(invocation -> {
            bothChecked.countDown();
            assertTrue(bothChecked.await(30, TimeUnit.SECONDS));
            try { return invocation.callRealMethod(); }
            catch (org.springframework.dao.DataIntegrityViolationException | ResponseStatusException e) { conflicts.incrementAndGet(); throw e; }
        }).when(target).reserve(eq(8104L), anyString(), any(), anyString());
        var snapshot = new StripeHostedGateway.Snapshot("cs_8104", "https://checkout.stripe.com/c/pay/cs_8104", "open", "unpaid", 2500L, "usd", "8104", null);
        when(hostedStripe.create(any())).thenReturn(snapshot);
        when(hostedStripe.retrieve("cs_8104")).thenReturn(snapshot);
        var mvc = MockMvcBuilders.standaloneSetup(new HostedCheckoutController(hosted)).setControllerAdvice(new PaymentExceptionHandler()).build();
        var pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> start = () -> mvc.perform(post("/payments/checkout/8104")
                    .header("X-User-Id", "owner").header("X-User-Username", "user")).andReturn().getResponse().getStatus();
            var first = pool.submit(start);
            var second = pool.submit(start);
            var statuses = java.util.List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertTrue(statuses.contains(200), statuses.toString());
            assertTrue(statuses.stream().allMatch(s -> s == 200 || s == 409), statuses.toString());
            assertEquals(1, conflicts.get(), "Both absent checks must reach competing inserts");
            assertEquals(1, repository.findByOrderId(8104L).size());
            verify(hostedStripe, atMost(2)).create(argThat(c -> c.getOrderId().equals(8104L) && c.getGeneration() == 0));
        } finally { pool.shutdownNow(); }
    }

    @Test void conflictSurvivesActualAdvice() throws Exception {
        payments.processPayment(request(8105, "STRIPE"));
        MockMvcBuilders.standaloneSetup(new ConflictEndpoint(payments)).setControllerAdvice(new PaymentExceptionHandler()).build()
                .perform(get("/test-conflict")).andExpect(status().isConflict());
    }

    @Test void heldOrderRejectsBothReservationPaths() {
        jdbc.update("INSERT INTO payment_order_guard(order_id, reconciliation_hold) VALUES (?, true)", 8110L);
        var payment = Payment.builder().orderId(8110L).userId("owner").paymentNumber("GUARD-8110")
                .amount(new BigDecimal("25.00")).currency(Currency.USD).method(PaymentMethodType.CARD)
                .status(org.de013.paymentservice.entity.enums.PaymentStatus.PENDING).build();
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> reservation.reserve(payment)).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> hostedStore.reserve(8110L, "owner", new BigDecimal("25.00"), "https://fixture.test")).getStatusCode().value());
        assertTrue(repository.findByOrderId(8110L).isEmpty());
        assertTrue(jdbc.queryForObject("SELECT reconciliation_hold FROM payment_order_guard WHERE order_id = ?", Boolean.class, 8110L));
    }

    @Test void concurrentFirstLockCannotClearHold() throws Exception {
        var locked = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var contending = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var writer = pool.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
                assertFalse(guard.lock(8111L));
                jdbc.update("UPDATE payment_order_guard SET reconciliation_hold = true WHERE order_id = ?", 8111L);
                locked.countDown();
                try { assertTrue(finish.await(20, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new RuntimeException(e); }
                return null;
            }));
            assertTrue(locked.await(20, TimeUnit.SECONDS));
            var contender = pool.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
                contending.countDown();
                return guard.lock(8111L);
            }));
            assertTrue(contending.await(20, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> contender.get(300, TimeUnit.MILLISECONDS));
            finish.countDown();
            writer.get(30, TimeUnit.SECONDS);
            assertTrue(contender.get(30, TimeUnit.SECONDS));
            assertTrue(jdbc.queryForObject("SELECT reconciliation_hold FROM payment_order_guard WHERE order_id = ?", Boolean.class, 8111L));
        } finally { finish.countDown(); pool.shutdownNow(); }
    }

    @Test void guardRequiresTransactionAndPaymentDefaultsToBlocking() {
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class, () -> guard.lock(8112L));
        assertTrue(Payment.builder().build().isBlocking());
        assertTrue(new Payment().isBlocking());
    }

    private Payment intent(long order, String id, org.de013.paymentservice.entity.enums.PaymentStatus status, String ref) {
        return repository.saveAndFlush(Payment.builder().orderId(order).userId("owner").paymentNumber("INT-" + order)
                .amount(new BigDecimal("25.00")).currency(Currency.USD).method(PaymentMethodType.CARD)
                .status(status).gatewayName("STRIPE").gatewayTxnRef(ref).stripePaymentIntentId(id).receiptEmail("fixture@example.test").build());
    }

    private String payload(String event, String type, String object, String id, String status) {
        return "{\"id\":\"" + event + "\",\"object\":\"event\",\"type\":\"" + type
                + "\",\"data\":{\"object\":{\"object\":\"" + object + "\",\"id\":\"" + id + "\",\"status\":\"" + status + "\"}}}";
    }

    private String signature(String payload) throws Exception {
        long timestamp = java.time.Instant.now().getEpochSecond();
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(webhook.getWebhookSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        return "t=" + timestamp + ",v1=" + java.util.HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private void send(String event, String type, String object, String id, String status) throws Exception {
        String body = payload(event, type, object, id, status);
        webhook.processStripeWebhook(body, signature(body));
    }

    @Test void verifiedReleaseAndLateSuccessHoldWithNewAttempt() throws Exception {
        var original = intent(8120, "pi_release_8120", org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, null);
        send("evt_cancel_8120", "payment_intent.canceled", "payment_intent", "pi_release_8120", "canceled");
        var released = repository.findById(original.getId()).orElseThrow();
        assertFalse(released.isBlocking()); assertTrue(released.isVerifiedRelease());
        reservation.reserve(Payment.builder().orderId(8120L).userId("owner").paymentNumber("NEW-8120")
                .amount(new BigDecimal("25.00")).currency(Currency.USD).method(PaymentMethodType.CARD)
                .status(org.de013.paymentservice.entity.enums.PaymentStatus.PENDING).build());
        assertThrows(ResponseStatusException.class, () -> reservation.reserve(Payment.builder().orderId(8120L).userId("owner").paymentNumber("ANOTHER-8120")
                .amount(new BigDecimal("25.00")).currency(Currency.USD).method(PaymentMethodType.CARD)
                .status(org.de013.paymentservice.entity.enums.PaymentStatus.PENDING).build()));
        send("evt_success_8120", "payment_intent.succeeded", "payment_intent", "pi_release_8120", "succeeded");
        var success = repository.findById(original.getId()).orElseThrow();
        assertEquals(org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, success.getStatus());
        assertFalse(success.isBlocking()); assertTrue(success.isVerifiedRelease());
        assertTrue(jdbc.queryForObject("select reconciliation_hold from payment_order_guard where order_id = ?", Boolean.class, 8120L));
        var newer = repository.findByOrderId(8120L).stream().filter(Payment::isBlocking).findFirst().orElseThrow();
        newer.setGatewayName("STRIPE"); newer.setStripePaymentIntentId("pi_new_8120"); repository.saveAndFlush(newer);
        send("evt_new_success_8120", "payment_intent.succeeded", "payment_intent", "pi_new_8120", "succeeded");
        assertEquals(org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, repository.findById(newer.getId()).orElseThrow().getStatus());
        verifyNoInteractions(orderClient, notifications);
    }

    @Test void uncorrelatedDeliveryRetriesThroughController() throws Exception {
        String body = payload("evt_retry_8121", "payment_intent.canceled", "payment_intent", "pi_retry_8121", "canceled");
        assertEquals(500, webhookController.handleStripeWebhook(body, signature(body)).getStatusCode().value());
        assertFalse(events.existsById("evt_retry_8121"));
        var payment = intent(8121, "pi_retry_8121", org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, null);
        assertEquals(200, webhookController.handleStripeWebhook(body, signature(body)).getStatusCode().value());
        assertTrue(events.existsById("evt_retry_8121"));
        assertFalse(repository.findById(payment.getId()).orElseThrow().isBlocking());
    }

    @Test void informationalIntentRequiresCorrelationWithoutFinancialMutation() throws Exception {
        String body = payload("evt_processing_8140", "payment_intent.processing", "payment_intent", "pi_processing_8140", "processing");
        assertEquals(500, webhookController.handleStripeWebhook(body, signature(body)).getStatusCode().value());
        assertFalse(events.existsById("evt_processing_8140"));
        var payment = intent(984140, "pi_processing_8140", org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, null);
        assertEquals(200, webhookController.handleStripeWebhook(body, signature(body)).getStatusCode().value());
        assertTrue(events.existsById("evt_processing_8140"));
        var unchanged = repository.findById(payment.getId()).orElseThrow();
        assertEquals(org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, unchanged.getStatus());
        assertTrue(unchanged.isBlocking()); assertFalse(unchanged.isVerifiedRelease());
        verifyNoInteractions(orderClient, notifications);
        for (String invalid : java.util.List.of(
                payload("evt_processing_bad_object_8140", "payment_intent.processing", "charge", "pi_processing_8140", "processing"),
                payload("evt_processing_bad_id_8140", "payment_intent.processing", "payment_intent", "", "processing"),
                "{\"id\":\"evt_processing_missing_id_8140\",\"type\":\"payment_intent.processing\",\"data\":{\"object\":{\"object\":\"payment_intent\"}}}")) {
            assertEquals(500, webhookController.handleStripeWebhook(invalid, signature(invalid)).getStatusCode().value());
        }
        assertFalse(events.existsById("evt_processing_bad_object_8140"));
        assertFalse(events.existsById("evt_processing_bad_id_8140"));
        assertFalse(events.existsById("evt_processing_missing_id_8140"));
    }

    @Test void invalidAndHostedEventsCannotRelease() throws Exception {
        var payment = intent(8122, "pi_invalid_8122", org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, null);
        String invalid = payload("evt_bad_sig_8122", "payment_intent.canceled", "payment_intent", "pi_invalid_8122", "canceled");
        assertThrows(RuntimeException.class, () -> webhook.processStripeWebhook(invalid, "invalid"));
        assertThrows(RuntimeException.class, () -> send("evt_bad_obj_8122", "payment_intent.canceled", "charge", "pi_invalid_8122", "canceled"));
        assertThrows(RuntimeException.class, () -> send("evt_bad_status_8122", "payment_intent.canceled", "payment_intent", "pi_invalid_8122", "processing"));
        assertThrows(RuntimeException.class, () -> send("evt_bad_id_8122", "payment_intent.canceled", "payment_intent", "", "canceled"));
        send("evt_other_type_8122", "customer.created", "payment_intent", "pi_invalid_8122", "canceled");
        send("evt_failed_8122", "payment_intent.payment_failed", "payment_intent", "pi_invalid_8122", "requires_payment_method");
        assertTrue(repository.findById(payment.getId()).orElseThrow().isBlocking());
        var hostedPayment = intent(8123, "pi_hosted_8123", org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, "cs_8123");
        send("evt_hosted_8123", "payment_intent.canceled", "payment_intent", "pi_hosted_8123", "canceled");
        assertTrue(repository.findById(hostedPayment.getId()).orElseThrow().isBlocking());
        var success = intent(8124, "pi_success_8124", org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, null);
        send("evt_cancel_8124", "payment_intent.canceled", "payment_intent", "pi_success_8124", "canceled");
        assertEquals(org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, repository.findById(success.getId()).orElseThrow().getStatus());
        assertThrows(SecurityException.class, () -> webhook.handlePaymentIntentCanceled(null));
        assertThrows(org.de013.paymentservice.exception.PaymentGatewayException.class, () -> webhook.processWebhookEvent(org.de013.paymentservice.dto.payment.StripeWebhookRequest.builder().type("payment_intent.canceled").build()));
    }

    @Test void lateSuccessWithoutNewAttemptAndConcurrentDuplicateSuccess() throws Exception {
        var released = intent(8125, "pi_release_8125", org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, null);
        send("evt_cancel_8125", "payment_intent.canceled", "payment_intent", "pi_release_8125", "canceled");
        send("evt_success_8125", "payment_intent.succeeded", "payment_intent", "pi_release_8125", "succeeded");
        assertFalse(repository.findById(released.getId()).orElseThrow().isBlocking());
        assertTrue(jdbc.queryForObject("select reconciliation_hold from payment_order_guard where order_id = ?", Boolean.class, 8125L));
        intent(8126, "pi_duplicate_8126", org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, null);
        String body = payload("evt_duplicate_8126", "payment_intent.succeeded", "payment_intent", "pi_duplicate_8126", "succeeded");
        String sig = signature(body);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Void> delivery = () -> { start.await(); webhook.processStripeWebhook(body, sig); return null; };
            var first = pool.submit(delivery); var second = pool.submit(delivery); start.countDown();
            first.get(30, TimeUnit.SECONDS); second.get(30, TimeUnit.SECONDS);
            send("evt_duplicate_other_8126", "payment_intent.succeeded", "payment_intent", "pi_duplicate_8126", "succeeded");
            verify(orderClient, times(1)).markOrderAsPaid(eq(8126L), anyLong(), anyString());
            verify(notifications, times(1)).sendEmail(any());
        } finally { pool.shutdownNow(); }
    }

    @Test void holdWinsBeforeConfirmAndCaptureDispatch() throws Exception {
        var confirm = intent(8130, "pi_confirm_8130", org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, null);
        var capture = intent(8131, "pi_capture_8131", org.de013.paymentservice.entity.enums.PaymentStatus.REQUIRES_CONFIRMATION, null);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(3);
        try {
            var writer = pool.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(s -> {
                guard.hold(8130L); guard.hold(8131L); locked.countDown();
                try { assertTrue(release.await(20, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new RuntimeException(e); }
                return null;
            }));
            assertTrue(locked.await(20, TimeUnit.SECONDS));
            var a = pool.submit(() -> assertThrows(ResponseStatusException.class, () -> payments.confirmPayment(confirm.getId(), "pm_test")));
            var b = pool.submit(() -> assertThrows(ResponseStatusException.class, () -> payments.capturePayment(capture.getId(), null)));
            release.countDown(); writer.get(30, TimeUnit.SECONDS);
            assertEquals(409, a.get(30, TimeUnit.SECONDS).getStatusCode().value());
            assertEquals(409, b.get(30, TimeUnit.SECONDS).getStatusCode().value());
            verify(stripe, never()).confirmPaymentIntent(any(), any());
            verify(stripe, never()).capturePayment(any(), any());
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void staleSnapshotsCannotOverrideReleaseOrSuccess() throws Exception {
        var stale = intent(8132, "pi_stale_8132", org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, null);
        send("evt_cancel_8132", "payment_intent.canceled", "payment_intent", "pi_stale_8132", "canceled");
        assertThrows(UnsupportedOperationException.class, () -> payments.savePaymentEntity(stale));
        payments.cancelPayment(stale.getId(), "local");
        payments.updatePaymentStatus(stale.getId(), org.de013.paymentservice.entity.enums.PaymentStatus.FAILED, "local");
        when(stripe.getPaymentIntent("pi_stale_8132")).thenReturn(StripePaymentResponse.builder().paymentIntentId("pi_stale_8132").status("succeeded").build());
        payments.syncPaymentStatusWithStripe(stale.getId());
        var current = repository.findById(stale.getId()).orElseThrow();
        assertEquals(org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, current.getStatus());
        assertFalse(current.isBlocking()); assertTrue(current.isVerifiedRelease());
        assertTrue(jdbc.queryForObject("select reconciliation_hold from payment_order_guard where order_id = ?", Boolean.class, 8132L));
        when(stripe.getPaymentIntent("pi_stale_8132")).thenReturn(StripePaymentResponse.builder().paymentIntentId("pi_stale_8132").status("processing").build());
        payments.syncPaymentStatusWithStripe(stale.getId());
        payments.updatePaymentStatus(stale.getId(), org.de013.paymentservice.entity.enums.PaymentStatus.FAILED, "stale");
        assertEquals(org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, repository.findById(stale.getId()).orElseThrow().getStatus());
        verifyNoInteractions(orderClient, notifications);
    }

    @Test void localStatusNeverReleases() {
        var one = intent(8133, "pi_local_8133", org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, null);
        payments.cancelPayment(one.getId(), "local");
        var two = intent(8134, "pi_local_8134", org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, null);
        payments.updatePaymentStatus(two.getId(), org.de013.paymentservice.entity.enums.PaymentStatus.FAILED, "local");
        assertTrue(repository.findById(one.getId()).orElseThrow().isBlocking());
        assertTrue(repository.findById(two.getId()).orElseThrow().isBlocking());
    }

    private void hostedOrder(long id) {
        var order = new OrderDto(); order.setId(id); order.setUserId("owner"); order.setStatus("PENDING");
        var money = new OrderDto.MoneyDto(); money.setAmount(new BigDecimal("25.00")); money.setCurrency("USD"); order.setTotalAmount(money);
        when(orders.read(id, "owner", "hosted-checkout")).thenReturn(ResponseEntity.ok(order));
    }

    @Test void hostedEvidenceCommitsBeforeHoldWinsDispatch() throws Exception {
        hostedOrder(8140);
        hostedStore.reserve(8140L, "owner", new BigDecimal("25.00"), "https://fixture.test/checkout/result");
        hostedStore.record(8140L, 0, "cs_8140", "OPEN", null);
        when(hostedStripe.retrieve("cs_8140")).thenReturn(new StripeHostedGateway.Snapshot("cs_8140", null, "complete", "paid", 2500L, "usd", "8140", "pi_hosted_8140"));
        var reached = new CountDownLatch(1); var resume = new CountDownLatch(1);
        var target = org.springframework.test.util.AopTestUtils.<HostedCheckoutStore>getTargetObject(hostedStore);
        doAnswer(inv -> { reached.countDown(); assertTrue(resume.await(20, TimeUnit.SECONDS)); return inv.callRealMethod(); }).when(target).markPaid(8140L);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var result = pool.submit(() -> hosted.read(8140L, "owner"));
            assertTrue(reached.await(20, TimeUnit.SECONDS));
            assertEquals(org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, repository.findByOrderId(8140L).get(0).getStatus());
            assertEquals("PAID", jdbc.queryForObject("select session_state from hosted_checkouts where order_id = ?", String.class, 8140L));
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(s -> guard.hold(8140L));
            resume.countDown(); assertEquals("PAID", result.get(30, TimeUnit.SECONDS).state());
            verify(orders, never()).markPaid(eq(8140L), any(), any(), any(), any());
        } finally { resume.countDown(); pool.shutdownNow(); }
    }

    @Test void hostedCreateStopsWhenHoldWins() throws Exception {
        hostedOrder(8141);
        hostedStore.reserve(8141L, "owner", new BigDecimal("25.00"), "https://fixture.test/checkout/result");
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(s -> guard.hold(8141L));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> hosted.start(8141L, "owner")).getStatusCode().value());
        verify(hostedStripe, never()).create(any());
    }

    @Test void hostedPaidEvidenceSurvivesOrderFailure() throws Exception {
        hostedOrder(8143);
        var checkout = hostedStore.reserve(8143L, "owner", new BigDecimal("25.00"), "https://fixture.test/checkout/result");
        hostedStore.record(8143L, 0, "cs_8143", "OPEN", null);
        when(hostedStripe.retrieve("cs_8143")).thenReturn(new StripeHostedGateway.Snapshot("cs_8143", null, "complete", "paid", 2500L, "usd", "8143", "pi_hosted_8143"));
        when(orders.markPaid(eq(8143L), any(), any(), any(), any())).thenReturn(ResponseEntity.status(503).build());
        assertThrows(ResponseStatusException.class, () -> hosted.read(8143L, "owner"));
        assertEquals(org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, repository.findById(checkout.getPaymentId()).orElseThrow().getStatus());
        assertEquals("PAID", jdbc.queryForObject("select session_state from hosted_checkouts where order_id = ?", String.class, 8143L));
        assertThrows(ResponseStatusException.class, () -> hostedStore.record(8143L, 0, "cs_8143", "PAID", "pi_other"));
        assertEquals("pi_hosted_8143", repository.findById(checkout.getPaymentId()).orElseThrow().getStripePaymentIntentId());
    }

    private org.de013.paymentservice.entity.Refund refund(Payment payment, String number, String stripeId, org.de013.paymentservice.entity.enums.RefundStatus status) {
        return refundRepository.saveAndFlush(org.de013.paymentservice.entity.Refund.builder().payment(payment).orderId(payment.getOrderId())
                .refundNumber(number).amount(new BigDecimal("5.00")).currency("USD").status(status).stripeRefundId(stripeId).build());
    }

    @Test void holdBlocksRefundCreateProcessAndApprove() throws Exception {
        var payment = intent(8150, "pi_refund_8150", org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, null);
        var first = refund(payment, "REF-8150-A", null, org.de013.paymentservice.entity.enums.RefundStatus.PENDING);
        var second = refund(payment, "REF-8150-B", null, org.de013.paymentservice.entity.enums.RefundStatus.PENDING);
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(s -> guard.hold(8150L));
        var request = org.de013.paymentservice.dto.refund.RefundRequest.builder().paymentId(payment.getId()).amount(new BigDecimal("5.00")).reason("requested_by_customer").build();
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> refunds.createRefund(request)).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> refunds.processRefund(first.getId())).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> refunds.approveRefund(second.getId(), "admin")).getStatusCode().value());
        verify(stripe, never()).createRefund(any());
    }

    @Test void heldRefundReadbackAndWebhookPreserveSuccessAndCleanup() throws Exception {
        var payment = intent(8151, "pi_refund_8151", org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, null);
        var pending = refund(payment, "REF-8151-A", "re_8151_a", org.de013.paymentservice.entity.enums.RefundStatus.PENDING);
        var failed = refund(payment, "REF-8151-B", "re_8151_b", org.de013.paymentservice.entity.enums.RefundStatus.FAILED);
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(s -> guard.hold(8151L));
        when(factory.getStripeGateway()).thenReturn(stripe);
        when(stripe.getRefundResponse("re_8151_a")).thenReturn(org.de013.paymentservice.dto.stripe.StripeRefundResponse.builder().refundId("re_8151_a").status("succeeded").build());
        refunds.syncRefundStatusWithStripe(pending.getId());
        refunds.updateRefundStatus(pending.getId(), org.de013.paymentservice.entity.enums.RefundStatus.FAILED, "stale");
        assertEquals(org.de013.paymentservice.entity.enums.RefundStatus.SUCCEEDED, refundRepository.findById(pending.getId()).orElseThrow().getStatus());
        send("evt_refund_8151", "refund.updated", "refund", "re_8151_b", "succeeded");
        assertEquals(org.de013.paymentservice.entity.enums.RefundStatus.SUCCEEDED, refundRepository.findById(failed.getId()).orElseThrow().getStatus());
        var old = refund(payment, "REF-8151-C", "re_8151_c", org.de013.paymentservice.entity.enums.RefundStatus.FAILED);
        refunds.cleanupOldFailedRefunds(java.time.LocalDateTime.now().plusDays(1));
        assertTrue(refundRepository.existsById(old.getId()));
        assertThrows(UnsupportedOperationException.class, () -> refunds.saveRefundEntity(failed));
        verify(stripe, never()).createRefund(any());
        verifyNoInteractions(orderClient, notifications);
    }

    @Test void refundDispatchLinearizesBeforeHold() throws Exception {
        var payment = intent(8152, "pi_refund_8152", org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, null);
        var pending = refund(payment, "REF-8152", null, org.de013.paymentservice.entity.enums.RefundStatus.PENDING);
        when(factory.getStripeGateway()).thenReturn(stripe);
        var entered = new CountDownLatch(1); var resume = new CountDownLatch(1);
        when(stripe.createRefund(any())).thenAnswer(inv -> {
            entered.countDown(); assertTrue(resume.await(20, TimeUnit.SECONDS));
            return org.de013.paymentservice.dto.stripe.StripeRefundResponse.builder().refundId("re_8152").status("succeeded").build();
        });
        var pool = Executors.newFixedThreadPool(2);
        try {
            var dispatch = pool.submit(() -> refunds.processRefund(pending.getId()));
            assertTrue(entered.await(20, TimeUnit.SECONDS));
            var holder = pool.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(s -> guard.hold(8152L)));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> holder.get(300, TimeUnit.MILLISECONDS));
            resume.countDown(); dispatch.get(30, TimeUnit.SECONDS); holder.get(30, TimeUnit.SECONDS);
            assertTrue(jdbc.queryForObject("select reconciliation_hold from payment_order_guard where order_id = ?", Boolean.class, 8152L));
            assertEquals(org.de013.paymentservice.entity.enums.RefundStatus.SUCCEEDED, refundRepository.findById(pending.getId()).orElseThrow().getStatus());
            verify(stripe, times(1)).createRefund(any());
        } finally { resume.countDown(); pool.shutdownNow(); }
    }

    @Test void stripeCreateResponseSerializesBeforeHold() throws Exception {
        var entered = new CountDownLatch(1); var resume = new CountDownLatch(1); var attempting = new CountDownLatch(1);
        when(stripe.createPaymentIntent(any())).thenAnswer(inv -> {
            entered.countDown(); assertTrue(resume.await(20, TimeUnit.SECONDS));
            return StripePaymentResponse.builder().paymentIntentId("pi_create_8160").status("processing").build();
        });
        var pool = Executors.newFixedThreadPool(2);
        try {
            var create = pool.submit(() -> payments.processPayment(request(8160, "STRIPE")));
            assertTrue(entered.await(20, TimeUnit.SECONDS));
            assertEquals(1, repository.findByOrderId(8160L).size(), "Reservation must be committed before provider dispatch");
            var holder = pool.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(s -> {
                attempting.countDown(); guard.hold(8160L);
            }));
            assertTrue(attempting.await(20, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> holder.get(300, TimeUnit.MILLISECONDS));
            resume.countDown(); create.get(30, TimeUnit.SECONDS); holder.get(30, TimeUnit.SECONDS);
            var payment = repository.findByOrderId(8160L).get(0);
            assertEquals("pi_create_8160", payment.getStripePaymentIntentId());
            assertEquals(org.de013.paymentservice.entity.enums.PaymentStatus.PROCESSING, payment.getStatus());
            assertTrue(payment.isBlocking()); assertFalse(payment.isVerifiedRelease());
            assertTrue(jdbc.queryForObject("select reconciliation_hold from payment_order_guard where order_id = ?", Boolean.class, 8160L));
            verifyNoInteractions(orderClient, notifications);
        } finally { resume.countDown(); pool.shutdownNow(); }
    }

    @Test void stripeConfirmResponseSerializesBeforeVerifiedCancelAndLateSuccess() throws Exception {
        var payment = intent(8161, "pi_confirm_8161", org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, null);
        var entered = new CountDownLatch(1); var resume = new CountDownLatch(1); var attempting = new CountDownLatch(1);
        when(stripe.confirmPaymentIntent(eq("pi_confirm_8161"), any())).thenAnswer(inv -> {
            entered.countDown(); assertTrue(resume.await(20, TimeUnit.SECONDS));
            return StripePaymentResponse.builder().paymentIntentId("pi_confirm_8161").status("processing").build();
        });
        var pool = Executors.newFixedThreadPool(2);
        try {
            var confirm = pool.submit(() -> payments.confirmPayment(payment.getId(), "pm_fixture"));
            assertTrue(entered.await(20, TimeUnit.SECONDS));
            var cancel = pool.submit(() -> { attempting.countDown(); send("evt_cancel_8161", "payment_intent.canceled", "payment_intent", "pi_confirm_8161", "canceled"); return null; });
            assertTrue(attempting.await(20, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> cancel.get(300, TimeUnit.MILLISECONDS));
            resume.countDown(); confirm.get(30, TimeUnit.SECONDS); cancel.get(30, TimeUnit.SECONDS);
            var released = repository.findById(payment.getId()).orElseThrow();
            assertEquals(org.de013.paymentservice.entity.enums.PaymentStatus.CANCELED, released.getStatus());
            assertFalse(released.isBlocking()); assertTrue(released.isVerifiedRelease());
            reservation.reserve(Payment.builder().orderId(8161L).userId("owner").paymentNumber("NEW-8161")
                    .amount(new BigDecimal("25.00")).currency(Currency.USD).method(PaymentMethodType.CARD)
                    .status(org.de013.paymentservice.entity.enums.PaymentStatus.PENDING).build());
            send("evt_late_8161", "payment_intent.succeeded", "payment_intent", "pi_confirm_8161", "succeeded");
            var success = repository.findById(payment.getId()).orElseThrow();
            assertEquals(org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, success.getStatus());
            assertFalse(success.isBlocking()); assertTrue(success.isVerifiedRelease());
            assertTrue(jdbc.queryForObject("select reconciliation_hold from payment_order_guard where order_id = ?", Boolean.class, 8161L));
            verifyNoInteractions(orderClient, notifications);
        } finally { resume.countDown(); pool.shutdownNow(); }
    }

    @Test void hostedReadbackAndLateSuccessSerializeWithoutDeadlock() throws Exception {
        var old = intent(8142, "pi_old_8142", org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, null);
        send("evt_cancel_8142", "payment_intent.canceled", "payment_intent", "pi_old_8142", "canceled");
        // Seed the already-existing hosted attempt directly; hosted retry creation remains conservative.
        var newer = reservation.reserve(Payment.builder().orderId(8142L).userId("owner").paymentNumber("HOSTED-8142")
                .amount(new BigDecimal("25.00")).currency(Currency.USD).method(PaymentMethodType.CARD)
                .gatewayName("STRIPE").status(org.de013.paymentservice.entity.enums.PaymentStatus.PENDING).build());
        jdbc.update("insert into hosted_checkouts(order_id,user_id,payment_id,amount,success_url,cancel_url,reserved_at,generation,session_id,session_state) values (?,?,?,?,?,?,now(),0,?,?)", 8142L, "owner", newer.getId(), new BigDecimal("25.00"), "https://fixture.test", "https://fixture.test", "cs_8142", "OPEN");
        hostedOrder(8142);
        when(hostedStripe.retrieve("cs_8142")).thenReturn(new StripeHostedGateway.Snapshot("cs_8142", null, "complete", "paid", 2500L, "usd", "8142", "pi_hosted_8142"));
        var entered = new CountDownLatch(1); var resume = new CountDownLatch(1);
        doAnswer(inv -> { entered.countDown(); assertTrue(resume.await(20, TimeUnit.SECONDS)); return java.util.Optional.of(jpa.find(Payment.class, newer.getId())); })
                .when(repository).findById(newer.getId());
        var pool = Executors.newFixedThreadPool(2);
        try {
            var read = pool.submit(() -> hosted.read(8142L, "owner"));
            assertTrue(entered.await(20, TimeUnit.SECONDS));
            var late = pool.submit(() -> { try { send("evt_success_8142", "payment_intent.succeeded", "payment_intent", "pi_old_8142", "succeeded"); } catch (Exception e) { throw new RuntimeException(e); } });
            assertThrows(java.util.concurrent.TimeoutException.class, () -> late.get(300, TimeUnit.MILLISECONDS));
            resume.countDown();
            late.get(30, TimeUnit.SECONDS); read.get(30, TimeUnit.SECONDS);
            assertTrue(jdbc.queryForObject("select reconciliation_hold from payment_order_guard where order_id = ?", Boolean.class, 8142L));
            assertEquals(org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, repository.findById(old.getId()).orElseThrow().getStatus());
            assertEquals(org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, repository.findById(newer.getId()).orElseThrow().getStatus());
        } finally { resume.countDown(); pool.shutdownNow(); }
    }
    @RestController static class ConflictEndpoint {
        private final PaymentService service;
        ConflictEndpoint(PaymentService service) { this.service = service; }
        @GetMapping("/test-conflict") void conflict() { service.processPayment(ProcessPaymentRequest.builder().orderId(8105L).userId("owner").amount(new BigDecimal("25.00")).currency(Currency.USD).paymentMethodType(PaymentMethodType.CARD).provider("STRIPE").build()); }
    }
}
