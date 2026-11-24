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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
        p.add("payment.internal.issuer-uri", () -> org.de013.paymentservice.security.CheckoutClosureSecurityTest.issuer);
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
    @MockitoSpyBean PaymentOrderGuard guard;
    @Autowired CompensationProofStore proofStore;
    @Autowired PaymentRetryAttempts retryAttempts;
    @Autowired CheckoutClosureService closures;
    @Autowired org.springframework.web.context.WebApplicationContext webContext;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired org.de013.paymentservice.controller.ReconciliationController reconciliation;
    @Autowired jakarta.persistence.EntityManager jpa;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired WebhookService webhook;
    @Autowired org.de013.paymentservice.controller.WebhookController webhookController;
    @Autowired org.de013.paymentservice.repository.ProcessedStripeEventRepository events;
    @Autowired RefundService refunds;
    @Autowired org.de013.paymentservice.repository.RefundRepository refundRepository;
    @MockitoBean org.de013.paymentservice.client.OrderServiceClient orderClient;
    @MockitoBean org.de013.paymentservice.client.UserServiceClient users;
    @MockitoBean org.de013.paymentservice.client.NotificationServiceClient notifications;
    @MockitoSpyBean PaymentRepository repository;
    @MockitoSpyBean HostedCheckoutStore hostedStore;
    @MockitoBean PaymentRequestValidator validator;
    @MockitoBean PaymentGatewayFactory factory;
    @MockitoBean StripePaymentGateway stripe;
    @MockitoBean StripeHostedGateway hostedStripe;
    @MockitoBean VnpayPaymentGateway vnpayGateway;
    @MockitoBean HostedOrderClient orders;

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

    @Test void reconciliationReadsHistoryAndDoesNotCreateOrMutateGuard() {
        long id = 299001L;
        var absent = reconciliation.get(id);
        assertFalse(absent.guardPresent());
        assertTrue(absent.attempts().isEmpty());
        assertEquals(0, jdbc.queryForObject("select count(*) from payment_order_guard where order_id=?", Integer.class, id));
        jdbc.update("insert into payment_order_guard(order_id, reconciliation_hold, close_operation_id, close_status, close_reason) values (?, true, ?, 'HOLD', 'RECONCILIATION_HOLD')", id, java.util.UUID.randomUUID());
        Long first = jdbc.queryForObject("insert into payments(payment_number, order_id, user_id, amount, currency, status, method, gateway_name, blocking) values ('recon-1', ?, 'owner', 25.00, 'USD', 'REFUNDED', 'CARD', 'STRIPE', false) returning id", Long.class, id);
        jdbc.update("insert into payments(payment_number, order_id, user_id, amount, currency, status, method, gateway_name, blocking) values ('recon-2', ?, 'owner', 26.00, 'USD', 'PENDING', 'CARD', 'STRIPE', false)", id);
        jdbc.update("insert into payment_transactions(payment_id, type, amount, status) values (?, 'CAPTURE', 25.00, 'SUCCEEDED')", first);
        var snapshot = reconciliation.get(id);
        assertTrue(snapshot.guardPresent());
        assertTrue(snapshot.reconciliationHold());
        assertEquals("HOLD", snapshot.closeStatus());
        assertEquals(2, snapshot.attempts().size());
        assertEquals("REFUNDED", snapshot.attempts().get(0).status());
        assertTrue(snapshot.attempts().get(0).successfulChargeOrCapture());
        assertFalse(snapshot.attempts().get(1).successfulChargeOrCapture());
        assertTrue(jdbc.queryForObject("select reconciliation_hold from payment_order_guard where order_id=?", Boolean.class, id));
    }

    @Test void stripeCreationProvenanceRequiresRealCreateIdAndModeAndRollsBackAtomically() throws Exception {
        long id = 210001L;
        for (Boolean mode : new Boolean[]{true, false}) {
            long current = id++;
            when(stripe.createPaymentIntent(any())).thenReturn(StripePaymentResponse.builder().paymentIntentId("pi_prov_" + current)
                    .status("processing").livemode(mode).createdByStripe(true).build());
            payments.processPayment(request(current, "STRIPE"));
            var persisted = repository.findByOrderId(current).get(0);
            assertTrue(persisted.isStripeIntentCreatedByService());
            assertEquals(mode, persisted.getStripeIntentLivemode());
            assertEquals("pi_prov_" + current, persisted.getStripePaymentIntentId());
        }
        for (int scenario = 0; scenario < 3; scenario++) {
            long current = id++;
            var response = StripePaymentResponse.builder().paymentIntentId(scenario == 1 ? null : "pi_untrusted_" + current)
                    .status("processing").livemode(scenario == 2 ? null : false).createdByStripe(scenario != 0).build();
            when(stripe.createPaymentIntent(any())).thenReturn(response);
            payments.processPayment(request(current, "STRIPE"));
            var persisted = repository.findByOrderId(current).get(0);
            assertFalse(persisted.isStripeIntentCreatedByService()); assertNull(persisted.getStripeIntentLivemode());
        }
        long syncId = id++;
        var legacy = intent(syncId, "pi_provenance_sync", org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, null);
        when(stripe.getPaymentIntent("pi_provenance_sync")).thenReturn(StripePaymentResponse.builder()
                .paymentIntentId("pi_provenance_sync").status("processing").livemode(false).createdByStripe(true).build());
        payments.syncPaymentStatusWithStripe(legacy.getId());
        assertFalse(repository.findById(legacy.getId()).orElseThrow().isStripeIntentCreatedByService());
        long rollbackId = id;
        when(stripe.createPaymentIntent(any())).thenReturn(StripePaymentResponse.builder().paymentIntentId("pi_provenance_rollback")
                .status("processing").livemode(false).createdByStripe(true).build());
        doThrow(new IllegalStateException("simulated save failure")).when(repository).save(any(Payment.class));
        assertThrows(RuntimeException.class, () -> payments.processPayment(request(rollbackId, "STRIPE")));
        var rolledBack = repository.findByOrderId(rollbackId).get(0);
        assertFalse(rolledBack.isStripeIntentCreatedByService()); assertNull(rolledBack.getStripeIntentLivemode());
        assertNull(rolledBack.getStripePaymentIntentId());
    }

    @Test void closureRacesActualReservationAndHostedProviderCreation() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var guardTarget = org.springframework.test.util.AopTestUtils.<PaymentOrderGuard>getTargetObject(guard);
            doAnswer(i -> {
                Object result = i.callRealMethod();
                entered.countDown(); assertTrue(release.await(20, TimeUnit.SECONDS));
                return result;
            }).when(guardTarget).requirePayable(eq(199301L));
            var payment = Payment.builder().orderId(199301L).userId("owner").paymentNumber("RACE-199301")
                    .amount(new BigDecimal("25.00")).currency(Currency.USD).method(PaymentMethodType.CARD)
                    .status(org.de013.paymentservice.entity.enums.PaymentStatus.PENDING).build();
            var reserve = pool.submit(() -> reservation.reserve(payment));
            assertTrue(entered.await(20, TimeUnit.SECONDS));
            var close = pool.submit(() -> closures.close(199301L, java.util.UUID.randomUUID()));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> close.get(300, TimeUnit.MILLISECONDS));
            release.countDown(); reserve.get(20, TimeUnit.SECONDS);
            assertEquals("HOLD", close.get(20, TimeUnit.SECONDS).status());
            assertEquals(1, repository.findByOrderId(199301L).size());

            var checkout = hostedStore.reserve(199302L, "owner", new BigDecimal("25.00"), "https://fixture.test/result");
            var order = new OrderDto(); order.setId(199302L); order.setUserId("owner"); order.setStatus("PENDING");
            var money = new OrderDto.MoneyDto(); money.setAmount(new BigDecimal("25.00")); money.setCurrency("USD"); order.setTotalAmount(money);
            when(orders.read(199302L, "owner", "hosted-checkout")).thenReturn(ResponseEntity.ok(order));
            var providerEntered = new CountDownLatch(1); var providerRelease = new CountDownLatch(1);
            when(hostedStripe.create(any())).thenAnswer(i -> {
                providerEntered.countDown(); assertTrue(providerRelease.await(20, TimeUnit.SECONDS));
                return new StripeHostedGateway.Snapshot("cs_99302", "https://fixture.test", "open", "unpaid", 2500L, "usd", "99302", null);
            });
            try {
                var create = pool.submit(() -> hostedStore.create(checkout));
                assertTrue(providerEntered.await(20, TimeUnit.SECONDS));
                var closeHosted = pool.submit(() -> closures.close(199302L, java.util.UUID.randomUUID()));
                assertThrows(java.util.concurrent.TimeoutException.class, () -> closeHosted.get(300, TimeUnit.MILLISECONDS));
                providerRelease.countDown(); create.get(20, TimeUnit.SECONDS);
                assertEquals("HOLD", closeHosted.get(20, TimeUnit.SECONDS).status());
                assertEquals(409, assertThrows(ResponseStatusException.class, () -> hostedStore.create(checkout)).getStatusCode().value());
                verify(hostedStripe, times(1)).create(any());
            } finally { providerRelease.countDown(); }
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"VNPAY", "HOSTED", "CONFIRM", "CAPTURE"})
    void closureBlocksActualPaymentEntrypoints(String entry) throws Exception {
        long id = switch (entry) { case "VNPAY" -> 199310L; case "HOSTED" -> 199311L; case "CONFIRM" -> 199312L; default -> 199313L; };
        Payment payment = null;
        org.de013.paymentservice.entity.HostedCheckout checkout = null;
        if (entry.equals("CONFIRM") || entry.equals("CAPTURE"))
            payment = intent(id, "pi_" + id, entry.equals("CONFIRM") ? org.de013.paymentservice.entity.enums.PaymentStatus.PENDING : org.de013.paymentservice.entity.enums.PaymentStatus.REQUIRES_CONFIRMATION, null);
        if (entry.equals("HOSTED")) checkout = hostedStore.reserve(id, "owner", new BigDecimal("25.00"), "https://fixture.test/result");
        assertEquals(entry.equals("VNPAY") ? "SAFE" : "HOLD", closures.close(id, java.util.UUID.randomUUID()).status());
        final Payment p = payment; final var c = checkout;
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> {
            switch (entry) {
                case "VNPAY" -> vnpay.createCheckout(request(id, "VNPAY"));
                case "HOSTED" -> hostedStore.create(c);
                case "CONFIRM" -> payments.confirmPayment(p.getId(), "pm_test");
                case "CAPTURE" -> payments.capturePayment(p.getId(), null);
            }
        }).getStatusCode().value());
        verify(vnpayGateway, never()).buildPaymentUrl(anyString(), anyLong(), anyString(), anyString(), anyString());
        verify(hostedStripe, never()).create(any());
        verify(stripe, never()).confirmPaymentIntent(any(), any());
        verify(stripe, never()).capturePayment(any(), any());
    }

    @Test void committedVerifiedLateSuccessDowngradesSafeWithoutDispatch() throws Exception {
        long id = 199320L; var op = java.util.UUID.randomUUID();
        assertEquals("SAFE", closures.close(id, op).status());
        var proof = jdbc.queryForMap("select close_safe_proof, close_safe_at from payment_order_guard where order_id=?", id);
        // Simulate a legacy writer that committed an unrecorded attempt after the zero-attempt proof.
        var payment = intent(id, "pi_late_99320", org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, null);
        send("evt_late_99320", "payment_intent.succeeded", "payment_intent", "pi_late_99320", "succeeded");
        assertEquals("SUCCEEDED", jdbc.queryForObject("select status from payments where id=?", String.class, payment.getId()));
        assertEquals("HOLD", closures.close(id, op).status());
        assertEquals(proof, jdbc.queryForMap("select close_safe_proof, close_safe_at from payment_order_guard where order_id=?", id));
        assertEquals(0, jdbc.queryForObject("select count(*) from payment_outbox where payment_id=?", Integer.class, payment.getId()));
        verify(orderClient, never()).markOrderAsPaid(eq(id), anyLong(), anyString());
        verify(notifications, never()).sendEmail(anyMap());
    }

    @Test void securedEndpointReplaysLateSuccessAndPublicCallbacksRemainAccessible() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(webContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        var issuer = org.de013.paymentservice.security.CheckoutClosureSecurityTest.issuer;
        String token = org.de013.paymentservice.security.CheckoutClosureSecurityTest.token(issuer, "payment-service", "order-service", true, java.time.Instant.now().plusSeconds(300), false);
        long id = 199340L; var op = java.util.UUID.randomUUID();
        String body = "{\"operationId\":\"" + op + "\"}";
        mvc.perform(post("/internal/checkout-closures/" + id).header("Authorization", "Bearer " + token).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("SAFE"));
        var proof = jdbc.queryForMap("select close_safe_proof,close_safe_at from payment_order_guard where order_id=?", id);
        // Legacy external writer fixture represents an attempt that escaped the current fence.
        var payment = intent(id, "pi_199340", org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, null);
        String event = payload("evt_199340", "payment_intent.succeeded", "payment_intent", "pi_199340", "succeeded");
        mvc.perform(post("/webhooks/stripe").header("Stripe-Signature", signature(event)).contentType("application/json").content(event)).andExpect(status().isOk());
        mvc.perform(post("/internal/checkout-closures/" + id).header("Authorization", "Bearer " + token).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("HOLD"));
        assertEquals("SUCCEEDED", jdbc.queryForObject("select status from payments where id=?", String.class, payment.getId()));
        assertEquals(proof, jdbc.queryForMap("select close_safe_proof,close_safe_at from payment_order_guard where order_id=?", id));
        verify(orderClient, never()).markOrderAsPaid(eq(id), anyLong(), anyString());
        mvc.perform(get("/webhooks/vnpay/ipn")).andExpect(status().isOk());
    }

    @Test void existingAttemptsRemainHoldWithoutDurableTerminalProof() throws Exception {
        long canceled = 199330L, failed = 199331L, mixed = 199332L;
        intent(canceled, "pi_199330", org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, null);
        send("evt_cancel_199330", "payment_intent.canceled", "payment_intent", "pi_199330", "canceled");
        assertEquals("HOLD", closures.close(canceled, java.util.UUID.randomUUID()).status());
        send("evt_success_199330", "payment_intent.succeeded", "payment_intent", "pi_199330", "succeeded");
        assertEquals("HOLD", jdbc.queryForObject("select close_status from payment_order_guard where order_id=?", String.class, canceled));
        intent(failed, "pi_199331", org.de013.paymentservice.entity.enums.PaymentStatus.FAILED, null);
        assertEquals("HOLD", closures.close(failed, java.util.UUID.randomUUID()).status());
        var old = intent(mixed, "pi_199332_old", org.de013.paymentservice.entity.enums.PaymentStatus.CANCELED, null);
        old.setBlocking(false); old.setVerifiedRelease(true); old.setPaymentNumber("INT-199332-old"); repository.saveAndFlush(old);
        intent(mixed, "pi_199332_new", org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, null);
        assertEquals("HOLD", closures.close(mixed, java.util.UUID.randomUUID()).status());
    }

    @Test void manualSuccessAfterSafeCommitsContradictionWithoutErasingProof() {
        long id = 199350L; var operation = java.util.UUID.randomUUID();
        assertEquals("SAFE", closures.close(id, operation).status());
        var proof = jdbc.queryForMap("select close_safe_proof,close_safe_at from payment_order_guard where order_id=?", id);
        // Legacy writer fixture represents an attempt that escaped the current fence.
        var payment = intent(id, "pi_199350", org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, null);
        payments.updatePaymentStatus(payment.getId(), org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, null);
        assertEquals("SUCCEEDED", jdbc.queryForObject("select status from payments where id=?", String.class, payment.getId()));
        assertEquals("HOLD", closures.close(id, operation).status());
        assertEquals(proof, jdbc.queryForMap("select close_safe_proof,close_safe_at from payment_order_guard where order_id=?", id));
        verify(orderClient, never()).markOrderAsPaid(eq(id), anyLong(), anyString());
    }

    @Test void disabledOutboxPreservesVnpayAndHostedDispatchAndHolds() throws Exception {
        var cfg = gatewayConfig.getGateways().getVnpay();
        var real = new VnpayPaymentGateway(gatewayConfig);
        when(vnpayGateway.verifyCallback(anyMap())).thenAnswer(i -> real.verifyCallback(i.getArgument(0)));
        for (long id : new long[]{99201, 99202}) {
            vnpay.createCheckout(request(id, "VNPAY"));
            var payment = repository.findByOrderId(id).get(0);
            payment.setReceiptEmail("fixture@example.test"); repository.saveAndFlush(payment);
            if (id == 99202) new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(s -> guard.hold(id));
            var fields = new java.util.TreeMap<String, String>();
            fields.put("vnp_TmnCode", cfg.getTmnCode()); fields.put("vnp_TxnRef", payment.getPaymentNumber());
            fields.put("vnp_Amount", Long.toString(payment.getProviderAmountVnd() * 100));
            fields.put("vnp_ResponseCode", "00"); fields.put("vnp_TransactionStatus", "00");
            fields.put("vnp_SecureHash", org.de013.paymentservice.gateway.vnpay.VnpayUtils.hmacSHA512(cfg.getHashSecret(), org.de013.paymentservice.gateway.vnpay.VnpayUtils.buildHashData(fields)));
            assertEquals("00", ipn.process(fields).get("RspCode"));
            verify(orderClient, id == 99201 ? times(1) : never()).markOrderAsPaid(eq(id), anyLong(), anyString());
            verify(notifications, id == 99201 ? times(1) : never()).sendEmail(argThat(m -> ("Thanh toán thành công cho Đơn hàng #" + id).equals(m.get("subject"))));
            assertEquals(0, jdbc.queryForObject("select count(*) from payment_outbox where payment_id=?", Integer.class, payment.getId()));
        }
        for (long id : new long[]{99203, 99204}) {
            var checkout = hostedStore.reserve(id, "owner", new BigDecimal("25.00"), "https://fixture.test/checkout/result");
            if (id == 99204) new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(s -> guard.hold(id));
            hostedStore.record(id, 0, "cs_" + id, "PAID", "pi_" + id);
            when(orders.markPaid(eq(id), anyLong(), anyString(), anyString(), anyString())).thenReturn(ResponseEntity.ok().build());
            hostedStore.markPaid(id);
            verify(orders, id == 99203 ? times(1) : never()).markPaid(eq(id), anyLong(), anyString(), anyString(), anyString());
            assertEquals(0, jdbc.queryForObject("select count(*) from payment_outbox where payment_id=?", Integer.class, checkout.getPaymentId()));
        }
    }

    @Test void inventoryNotReadyBlocksActualMutationEntrypoints() throws Exception {
        var realValidator = new PaymentRequestValidator(orderClient, users);
        doAnswer(i -> { realValidator.validatePaymentRequest(i.getArgument(0)); return null; }).when(validator).validatePaymentRequest(any());
        doAnswer(i -> { realValidator.validatePaymentAmount(i.getArgument(0), i.getArgument(1), i.getArgument(2), i.getArgument(3)); return null; })
                .when(validator).validatePaymentAmount(anyLong(), any(), any(), anyString());
        when(orderClient.getOrderById(anyLong())).thenAnswer(i -> {
            var order = new OrderDto(); order.setId(i.getArgument(0)); order.setUserId("owner"); order.setStatus("PENDING"); order.setInventoryReady(false);
            var money = new OrderDto.MoneyDto(); money.setAmount(new BigDecimal("25.00")); money.setCurrency("USD"); order.setTotalAmount(money);
            return ResponseEntity.ok(order);
        });
        assertThrows(org.de013.common.exception.ConflictException.class, () -> payments.processPayment(request(8301, "STRIPE")));
        assertThrows(org.de013.common.exception.ConflictException.class, () -> vnpay.createCheckout(request(8302, "VNPAY")));
        assertTrue(repository.findByOrderId(8301L).isEmpty()); assertTrue(repository.findByOrderId(8302L).isEmpty());
        var confirm = intent(8303, "pi_confirm_8303", org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, null);
        var capture = intent(8304, "pi_capture_8304", org.de013.paymentservice.entity.enums.PaymentStatus.REQUIRES_CONFIRMATION, null);
        assertThrows(org.de013.common.exception.ConflictException.class, () -> payments.confirmPayment(confirm.getId(), "pm_test"));
        assertThrows(org.de013.common.exception.ConflictException.class, () -> payments.capturePayment(capture.getId(), null));
        verify(stripe, never()).createPaymentIntent(any()); verify(stripe, never()).confirmPaymentIntent(any(), any());
        verify(stripe, never()).capturePayment(any(), any());
        verify(vnpayGateway, never()).buildPaymentUrl(anyString(), anyLong(), anyString(), anyString(), anyString());
        var order = orderClient.getOrderById(8305L).getBody();
        when(orders.read(8305L, "owner", "hosted-checkout")).thenReturn(ResponseEntity.ok(order));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> hosted.start(8305L, "owner")).getStatusCode().value());
        var reserved = hostedStore.reserve(8305L, "owner", new BigDecimal("25.00"), "https://fixture.test/checkout/result");
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> hostedStore.create(reserved)).getStatusCode().value());
        verify(hostedStripe, never()).create(any());
    }

    @Test void inventoryNotReadyDoesNotDiscardHostedPaidEvidence() throws Exception {
        hostedOrder(8306);
        var checkout = hostedStore.reserve(8306L, "owner", new BigDecimal("25.00"), "https://fixture.test/checkout/result");
        hostedStore.record(8306L, 0, "cs_8306", "OPEN", null);
        var order = orders.read(8306L, "owner", "hosted-checkout").getBody();
        order.setInventoryReady(false); order.setStatus("CANCELLED");
        when(hostedStripe.retrieve("cs_8306")).thenReturn(new StripeHostedGateway.Snapshot("cs_8306", null, "complete", "paid", 2500L, "usd", "8306", "pi_8306"));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> hosted.read(8306L, "owner")).getStatusCode().value());
        assertEquals(org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, repository.findById(checkout.getPaymentId()).orElseThrow().getStatus());
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
                     var s = c.prepareStatement("select provider_amount_vnd, provider_currency, provider_exchange_rate, provider_quoted_at from payments where payment_number = ?")) {
                    s.setString(1, ref);
                    try (var rows = s.executeQuery()) {
                        assertTrue(rows.next()); assertEquals(amount, rows.getLong(1)); assertEquals("VND", rows.getString(2));
                        assertEquals(originalRate, rows.getLong(3)); assertNotNull(rows.getTimestamp(4));
                    }
                }
                return realGateway.buildPaymentUrl(ref, amount, i.getArgument(2), i.getArgument(3), i.getArgument(4));
            });
            var before = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
            var result = vnpay.createCheckout(request(8991, "VNPAY"));
            var after = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
            var payment = repository.findByOrderId(8991L).get(0);
            long wire = Math.round(25.0 * originalRate) * 100;
            assertTrue(result.getRedirectUrl().contains("vnp_Amount=" + wire + "&"));
            assertThrows(IllegalArgumentException.class, () -> realGateway.buildPaymentUrl("ref", Long.MAX_VALUE / 100 + 1, "order", "127.0.0.1", "20260101000000"));
            cfg.setVndRate(originalRate + 1);
            var snapshot = repository.findByOrderId(8991L).get(0);
            assertEquals(originalRate, snapshot.getProviderExchangeRate());
            assertNotNull(snapshot.getProviderQuotedAt());
            assertFalse(snapshot.getProviderQuotedAt().isBefore(before));
            assertFalse(snapshot.getProviderQuotedAt().isAfter(after));
            var fields = new java.util.TreeMap<String, String>();
            fields.put("vnp_TmnCode", cfg.getTmnCode()); fields.put("vnp_TxnRef", payment.getPaymentNumber());
            fields.put("vnp_Amount", Long.toString(wire)); fields.put("vnp_ResponseCode", "00"); fields.put("vnp_TransactionStatus", "00");
            fields.put("vnp_SecureHash", org.de013.paymentservice.gateway.vnpay.VnpayUtils.hmacSHA512(cfg.getHashSecret(), org.de013.paymentservice.gateway.vnpay.VnpayUtils.buildHashData(fields)));
            assertEquals("00", ipn.process(fields).get("RspCode"));
            assertEquals(org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, repository.findByPaymentNumber(payment.getPaymentNumber()).orElseThrow().getStatus());
        } finally { cfg.setVndRate(originalRate); }
    }

    @Test void vnpayRoundsHalfUpAndAcceptsWireMaximum() {
        var cfg = gatewayConfig.getGateways().getVnpay();
        long rate = cfg.getVndRate();
        try {
            cfg.setVndRate(1);
            var half = request(8988, "VNPAY"); half.setAmount(new BigDecimal("0.50"));
            vnpay.createCheckout(half);
            assertEquals(1L, repository.findByOrderId(8988L).get(0).getProviderAmountVnd());
            var max = request(8989, "VNPAY"); max.setAmount(new BigDecimal("92233720368547758"));
            vnpay.createCheckout(max);
            assertEquals(Long.MAX_VALUE / 100, repository.findByOrderId(8989L).get(0).getProviderAmountVnd());
        } finally { cfg.setVndRate(rate); }
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

    private void refundHttp(String body) throws Exception {
        MockMvcBuilders.standaloneSetup(webhookController).build().perform(post("/webhooks/stripe")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).header("Stripe-Signature", signature(body)).content(body))
                .andExpect(status().isOk());
    }

    private String refundEvent(String event, String type, String status, String amount, String currency, String intent, String charge, String mode, String account) {
        return "{\"id\":\"" + event + "\",\"object\":\"event\",\"type\":\"" + type + "\",\"livemode\":" + mode
                + (account == null ? "" : ",\"account\":\"" + account + "\"")
                + ",\"data\":{\"object\":{\"object\":\"refund\",\"id\":\"" + (event.contains("18171") ? "re_18171" : "re_18170") + "\",\"status\":\"" + status
                + "\",\"amount\":" + amount + ",\"currency\":\"" + currency + "\",\"payment_intent\":\"" + intent + "\",\"charge\":\"" + charge + "\"}}}";
    }

    @Test void signedRefundLateFailurePersistsContradictionAndDeduplicates() throws Exception {
        var payment = intent(18170, "pi_18170", org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, null);
        payment.setStripeIntentCreatedByService(true); payment.setStripeIntentLivemode(false); repository.saveAndFlush(payment);
        var r = refund(payment, "REF-18170", "re_18170", org.de013.paymentservice.entity.enums.RefundStatus.SUCCEEDED);
        r.setStripeChargeId("ch_18170"); r.setStripePaymentIntentId("pi_18170"); refundRepository.saveAndFlush(r);
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(s -> guard.permanentFence(18170L));
        String failed = refundEvent("evt_rf_18170_a", "refund.failed", "failed", "500", "usd", "pi_18170", "ch_18170", "false", null);
        refundHttp(failed);
        long version = jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, 18170L);
        assertTrue(version > 0); assertTrue(jdbc.queryForObject("select reconciliation_hold from payment_order_guard where order_id=?", Boolean.class, 18170L));
        assertEquals(org.de013.paymentservice.entity.enums.RefundStatus.SUCCEEDED, refundRepository.findById(r.getId()).orElseThrow().getStatus());
        refundHttp(failed);
        refundHttp(refundEvent("evt_rf_18170_b", "refund.updated", "failed", "500", "usd", "pi_18170", "ch_18170", "false", null));
        assertEquals(version, jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, 18170L));
        assertEquals(2, jdbc.queryForObject("select count(*) from stripe_financial_observation where order_id=? and status='failed'", Integer.class, 18170L));
    }

    @Test void signedRefundPendingAndMismatchesNeverInventSuccess() throws Exception {
        var payment = intent(18171, "pi_18171", org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, null);
        payment.setStripeIntentCreatedByService(true); payment.setStripeIntentLivemode(false); repository.saveAndFlush(payment);
        var r = refund(payment, "REF-18171", "re_18171", org.de013.paymentservice.entity.enums.RefundStatus.PENDING);
        r.setStripeChargeId("ch_18171"); r.setStripePaymentIntentId("pi_18171"); refundRepository.saveAndFlush(r);
        refundHttp(refundEvent("evt_rf_18171_pending", "refund.created", "pending", "500", "usd", "pi_18171", "ch_18171", "false", null));
        assertNotEquals(org.de013.paymentservice.entity.enums.RefundStatus.SUCCEEDED, refundRepository.findById(r.getId()).orElseThrow().getStatus());
        String[][] cases = {{"501","usd","pi_18171","ch_18171","false",""},{"500","eur","pi_18171","ch_18171","false",""},
                {"500","usd","pi_wrong","ch_18171","false",""},{"500","usd","pi_18171","ch_wrong","false",""},
                {"500","usd","pi_18171","ch_18171","true",""},{"500","usd","pi_18171","ch_18171","false","acct_other"}};
        for (int i = 0; i < cases.length; i++) {
            var c = cases[i]; refundHttp(refundEvent("evt_rf_18171_" + i, "refund.updated", "succeeded", c[0], c[1], c[2], c[3], c[4], c[5].isEmpty() ? null : c[5]));
            assertNotEquals(org.de013.paymentservice.entity.enums.RefundStatus.SUCCEEDED, refundRepository.findById(r.getId()).orElseThrow().getStatus());
        }
        assertTrue(jdbc.queryForObject("select reconciliation_hold from payment_order_guard where order_id=?", Boolean.class, 18171L));
    }

    @Test void signedRefundForeignIntentOnlyVersionsLocalOrderAndOppositeEventsDoNotDeadlock() throws Exception {
        var a = intent(18401, "pi_18401", org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, null);
        var b = intent(18402, "pi_18402", org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, null);
        for (var p : java.util.List.of(a, b)) { p.setStripeIntentCreatedByService(true); p.setStripeIntentLivemode(false); repository.saveAndFlush(p); }
        var ra = refund(a, "REF-18401", "re_18401", org.de013.paymentservice.entity.enums.RefundStatus.SUCCEEDED);
        var rb = refund(b, "REF-18402", "re_18402", org.de013.paymentservice.entity.enums.RefundStatus.SUCCEEDED);
        ra.setStripeChargeId("ch_18401"); ra.setStripePaymentIntentId("pi_18401"); refundRepository.saveAndFlush(ra);
        rb.setStripeChargeId("ch_18402"); rb.setStripePaymentIntentId("pi_18402"); refundRepository.saveAndFlush(rb);
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(s -> { guard.permanentFence(18401L); guard.permanentFence(18402L); });
        String first = refundEvent("evt_rf_cross_a", "refund.updated", "failed", "500", "usd", "pi_18402", "ch_18401", "false", null).replace("re_18170", "re_18401");
        refundHttp(first);
        assertEquals  (1L, jdbc.queryForObject("select financial_version from payment_order_guard where order_id=18401", Long.class));
        assertEquals  (0L, jdbc.queryForObject("select financial_version from payment_order_guard where order_id=18402", Long.class));
        assertTrue(jdbc.queryForObject("select reconciliation_hold from payment_order_guard where order_id=18401", Boolean.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from stripe_financial_observation where event_id='evt_rf_cross_a' and order_id=18401", Integer.class));
        String oppositeA = first.replace("evt_rf_cross_a", "evt_rf_cross_a2").replace("500", "501");
        String oppositeB = refundEvent("evt_rf_cross_b", "refund.updated", "failed", "500", "usd", "pi_18401", "ch_18402", "false", null).replace("re_18170", "re_18402");
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var fa = pool.submit(() -> { start.await(); refundHttp(oppositeA); return null; });
            var fb = pool.submit(() -> { start.await(); refundHttp(oppositeB); return null; });
            start.countDown(); fa.get(15, TimeUnit.SECONDS); fb.get(15, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertEquals(2L, jdbc.queryForObject("select financial_version from payment_order_guard where order_id=18401", Long.class));
        assertEquals(1L, jdbc.queryForObject("select financial_version from payment_order_guard where order_id=18402", Long.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from stripe_financial_observation where (event_id='evt_rf_cross_a2' and order_id<>18401) or (event_id='evt_rf_cross_b' and order_id<>18402)", Integer.class));
    }

    @Test void signedRefundFractionalAndOverflowAmountsAreNeverTruncated() throws Exception {
        var p = intent(18403, "pi_18403", org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, null);
        p.setStripeIntentCreatedByService(true); p.setStripeIntentLivemode(false); repository.saveAndFlush(p);
        var r = refund(p, "REF-18403", "re_18403", org.de013.paymentservice.entity.enums.RefundStatus.PENDING);
        r.setStripeChargeId("ch_18403"); r.setStripePaymentIntentId("pi_18403"); refundRepository.saveAndFlush(r);
        int i = 0;
        for (String amount : java.util.List.of("500.9", "9223372036854775808", "500.00000000000001", "500.00000000000000000000000000000000000000000001")) {
            String event = "evt_rf_precision_" + i++;
            refundHttp(refundEvent(event, "refund.updated", "succeeded", amount, "usd", "pi_18403", "ch_18403", "false", null).replace("re_18170", "re_18403"));
            assertNotEquals(org.de013.paymentservice.entity.enums.RefundStatus.SUCCEEDED, refundRepository.findById(r.getId()).orElseThrow().getStatus());
            assertEquals(1, jdbc.queryForObject("select count(*) from stripe_financial_observation where event_id=? and amount is null and order_id=18403", Integer.class, event));
        }
        assertTrue(jdbc.queryForObject("select reconciliation_hold from payment_order_guard where order_id=18403", Boolean.class));
    }

    @Test void signedSameStatusIntentFactsInvalidateProofAndDeduplicate() throws Exception {
        for (int i = 0; i < 5; i++) {
            long order = 18500 + i;
            String pi = "pi_facts_" + i, charge = "ch_facts_" + i;
            var p = intent(order, pi, org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, null);
            p.setStripeIntentCreatedByService(true); p.setStripeIntentLivemode(false); repository.saveAndFlush(p);
            jdbc.update("insert into payment_transactions(payment_id,type,amount,status,stripe_charge_id,stripe_payment_intent_id) values (?,'CAPTURE',25,'SUCCEEDED',?,?)", p.getId(), charge, pi);
            var request = new CompensationProofStore.Request(java.util.UUID.randomUUID(), 1, p.getId(), p.getPaymentNumber(), new BigDecimal("25.00"), "USD", null);
            var claim = proofStore.begin(order, request);
            String baseline = "{\"id\":\"evt_facts_base_" + i + "\",\"object\":\"event\",\"type\":\"payment_intent.succeeded\",\"livemode\":false,\"data\":{\"object\":{\"object\":\"payment_intent\",\"id\":\"" + pi + "\",\"status\":\"succeeded\",\"amount\":2500,\"currency\":\"usd\",\"latest_charge\":\"" + charge + "\"}}}";
            refundHttp(baseline);
            // Reclaim a snapshot after the baseline was ingested.
            jdbc.update("update compensation_operation set claim_until=clock_timestamp()-interval '1 second' where order_id=?", order);
            claim = proofStore.begin(order, request);
            var evidence = new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.Evidence("acct_test", false, java.time.Instant.now(), java.util.List.of(
                    new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.ChargeEvidence(charge, pi, 2500, "succeeded", java.util.List.of(
                            new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.RefundEvidence("re_facts_" + i, 2500, "succeeded")))));
            var completed = proofStore.complete(order, request.operationId(), claim.token(), new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.Result(true, null, evidence));
            assertEquals("COMPENSATED", completed.status(), completed.reason());
            long version = completed.financialVersion();
            refundHttp(baseline.replace("evt_facts_base_", "evt_facts_duplicate_"));
            assertEquals(version, proofStore.view(order, request.operationId()).financialVersion());
            String changed = baseline.replace("evt_facts_base_", "evt_facts_changed_");
            changed = switch (i) {
                case 0 -> changed.replace("2500", "2501");
                case 1 -> changed.replace("\"usd\"", "\"eur\"");
                case 2 -> changed.replace("\"livemode\":false", "\"livemode\":true");
                case 3 -> changed.replace(charge, charge + "_new");
                default -> changed.replace("\"livemode\":false", "\"livemode\":false,\"account\":\"acct_other\"");
            };
            refundHttp(changed);
            var held = proofStore.view(order, request.operationId());
            assertEquals("HOLD", held.status()); assertEquals(version + 1, held.financialVersion());
            assertEquals(completed.proofId(), held.proofId()); assertEquals(completed.proofVersion(), held.proofVersion());
            assertEquals(1, jdbc.queryForObject("select count(*) from payment_transactions where payment_id=? and stripe_charge_id=?", Integer.class, p.getId(), charge));
            assertEquals(1, jdbc.queryForObject("select count(*) from compensation_proof where proof_id=?", Integer.class, completed.proofId()));
            if (i == 4) {
                assertTrue(jdbc.queryForObject("select reconciliation_hold from payment_order_guard where order_id=?", Boolean.class, order));
                assertEquals(1, jdbc.queryForObject("select count(*) from stripe_financial_observation where event_id=? and order_id is null and account_id='acct_other'", Integer.class, "evt_facts_changed_" + i));
                jdbc.update("delete from stripe_financial_observation where event_id=?", "evt_facts_changed_" + i);
            }
        }
    }

    @Test void stripeIntentAndChargeObservationsVersionOnlyChangedFacts() throws Exception {
        intent(18160, "pi_18160", org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, null);
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(s -> guard.permanentFence(18160L));
        send("evt_intent_18160_a", "payment_intent.succeeded", "payment_intent", "pi_18160", "succeeded");
        long version = jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, 18160L);
        assertTrue(version > 0);
        send("evt_intent_18160_b", "payment_intent.succeeded", "payment_intent", "pi_18160", "succeeded");
        assertEquals(++version, jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, 18160L));
        String charge = "{\"id\":\"%s\",\"type\":\"charge.succeeded\",\"livemode\":false,\"data\":{\"object\":{\"object\":\"charge\",\"id\":\"ch_18160\",\"payment_intent\":\"pi_18160\",\"status\":\"succeeded\",\"amount\":2500,\"currency\":\"usd\"}}}";
        String first = charge.formatted("evt_charge_18160_a"); webhook.processStripeWebhook(first, signature(first));
        assertEquals(version + 1, jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, 18160L));
        String duplicate = charge.formatted("evt_charge_18160_b"); webhook.processStripeWebhook(duplicate, signature(duplicate));
        assertEquals(version + 1, jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, 18160L));
    }

    @Test void disputeAndUnresolvedObservationRemainDurable() throws Exception {
        intent(18161, "pi_18161", org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, null);
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(s -> guard.permanentFence(18161L));
        String body = "{\"id\":\"evt_dispute_18161\",\"type\":\"charge.dispute.created\",\"livemode\":false,\"data\":{\"object\":{\"object\":\"dispute\",\"id\":\"dp_18161\",\"charge\":\"ch_18161\",\"payment_intent\":\"pi_18161\",\"status\":\"needs_response\",\"amount\":2500,\"currency\":\"usd\"}}}";
        webhook.processStripeWebhook(body, signature(body));
        assertEquals(1L, jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, 18161L));
        String unknown = "{\"id\":\"evt_dispute_unknown_18161\",\"type\":\"charge.dispute.created\",\"livemode\":false,\"data\":{\"object\":{\"object\":\"dispute\",\"id\":\"dp_unknown_18161\",\"charge\":\"ch_unknown_18161\"}}}";
        webhook.processStripeWebhook(unknown, signature(unknown));
        assertEquals(1, jdbc.queryForObject("select count(*) from stripe_financial_observation where event_id=? and order_id is null", Integer.class, "evt_dispute_unknown_18161"));
        jdbc.update("delete from stripe_financial_observation where event_id=?", "evt_dispute_unknown_18161");
    }

    @Test void vnpayRepeatedFailureAndSuccessVersionActualTransitions() {
        var cfg = gatewayConfig.getGateways().getVnpay();
        when(vnpayGateway.verifyCallback(anyMap())).thenReturn(true);
        var payment = repository.saveAndFlush(Payment.builder().orderId(18162L).userId("owner").paymentNumber("VNP-18162")
                .amount(new BigDecimal("25.00")).currency(Currency.USD).method(PaymentMethodType.WALLET)
                .status(org.de013.paymentservice.entity.enums.PaymentStatus.PENDING).gatewayName("VNPAY")
                .providerAmountVnd(100L).providerCurrency("VND").build());
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(s -> guard.permanentFence(18162L));
        var fields = new java.util.HashMap<String, String>();
        fields.put("vnp_TmnCode", cfg.getTmnCode()); fields.put("vnp_TxnRef", payment.getPaymentNumber()); fields.put("vnp_Amount", "10000");
        fields.put("vnp_ResponseCode", "01"); fields.put("vnp_TransactionStatus", "01");
        assertEquals("00", ipn.process(fields).get("RspCode"));
        assertEquals(1L, jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, 18162L));
        assertEquals("00", ipn.process(fields).get("RspCode"));
        assertEquals(1L, jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, 18162L));
        fields.put("vnp_ResponseCode", "00"); fields.put("vnp_TransactionStatus", "00");
        assertEquals("00", ipn.process(fields).get("RspCode"));
        assertEquals(2L, jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, 18162L));
    }

    @Test void orderOutageDoesNotRollBackProviderTruth() throws Exception {
        var loggers = java.util.List.of(PaymentServiceImpl.class, WebhookServiceImpl.class, VnpayIpnService.class).stream()
                .map(c -> (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(c)).toList();
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); loggers.forEach(l -> l.addAppender(appender));
        try {
            long order = 99300;
            for (String flow : java.util.List.of("payment", "webhook", "vnpay")) {
                for (boolean failed : new boolean[]{false, true}) for (boolean transport : new boolean[]{false, true}) {
                    long id = ++order;
                    reset(orderClient, notifications);
                    if (transport) {
                        when(orderClient.markOrderAsPaid(anyLong(), anyLong(), anyString())).thenThrow(new IllegalStateException("transport"));
                        when(orderClient.markOrderPaymentFailed(anyLong(), anyString())).thenThrow(new IllegalStateException("transport"));
                    } else {
                        when(orderClient.markOrderAsPaid(anyLong(), anyLong(), anyString())).thenReturn(ResponseEntity.status(503).build());
                        when(orderClient.markOrderPaymentFailed(anyLong(), anyString())).thenReturn(ResponseEntity.status(503).build());
                    }
                    appender.list.clear();
                    Long paymentId;
                    String event = "evt_outage_" + id;
                    if (flow.equals("payment")) {
                        when(stripe.createPaymentIntent(any())).thenReturn(StripePaymentResponse.builder().paymentIntentId("pi_outage_" + id).status(failed ? "failed" : "succeeded").build());
                        var req = request(id, "STRIPE"); req.setReceiptEmail("fixture@example.test");
                        payments.processPayment(req);
                        paymentId = repository.findByOrderId(id).get(0).getId();
                        assertEquals(1, jdbc.queryForObject("select count(*) from payment_transactions where payment_id=?", Integer.class, paymentId));
                    } else if (flow.equals("webhook")) {
                        var payment = intent(id, "pi_outage_" + id, org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, null);
                        paymentId = payment.getId();
                        String body = payload(event, failed ? "payment_intent.payment_failed" : "payment_intent.succeeded", "payment_intent", "pi_outage_" + id, failed ? "requires_payment_method" : "succeeded");
                        assertEquals(200, webhookController.handleStripeWebhook(body, signature(body)).getStatusCode().value());
                        assertTrue(events.existsById(event));
                        assertEquals(200, webhookController.handleStripeWebhook(body, signature(body)).getStatusCode().value());
                    } else {
                        vnpay.createCheckout(request(id, "VNPAY"));
                        var payment = repository.findByOrderId(id).get(0); paymentId = payment.getId();
                        payment.setReceiptEmail("fixture@example.test"); repository.saveAndFlush(payment);
                        var cfg = gatewayConfig.getGateways().getVnpay();
                        when(vnpayGateway.verifyCallback(anyMap())).thenReturn(true);
                        var fields = new java.util.HashMap<String, String>();
                        fields.put("vnp_TmnCode", cfg.getTmnCode()); fields.put("vnp_TxnRef", payment.getPaymentNumber());
                        fields.put("vnp_Amount", Long.toString(payment.getProviderAmountVnd() * 100));
                        fields.put("vnp_ResponseCode", failed ? "01" : "00"); fields.put("vnp_TransactionStatus", failed ? "01" : "00");
                        assertEquals("00", ipn.process(fields).get("RspCode"));
                        if (!failed) { assertTrue(events.existsById("vnpay_" + payment.getPaymentNumber())); assertEquals("02", ipn.process(fields).get("RspCode")); }
                    }
                    assertEquals(failed ? "FAILED" : "SUCCEEDED", jdbc.queryForObject("select status from payments where id=?", String.class, paymentId));
                    if (failed) verify(orderClient, times(1)).markOrderPaymentFailed(eq(id), anyString());
                    else {
                        verify(orderClient, times(1)).markOrderAsPaid(eq(id), anyLong(), anyString());
                        verify(notifications, times(1)).sendEmail(anyMap());
                    }
                    String status = failed ? "PAYMENT_FAILED" : "PAID";
                    assertTrue(appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("Failed to update") && e.getFormattedMessage().contains(Long.toString(id)) && e.getFormattedMessage().contains(status) && (transport || e.getFormattedMessage().contains("503"))));
                    assertFalse(appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("Successfully updated order " + id)));
                }
            }
        } finally { loggers.forEach(l -> l.detachAppender(appender)); appender.stop(); }
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

    @Test void proofClaimBlocksRefundMutationsAndCleanupWithoutProviderDispatch() throws Exception {
        var p = intent(18700, "pi_c1_18700", org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, null);
        p.setStripeIntentCreatedByService(true); p.setStripeIntentLivemode(false); repository.saveAndFlush(p);
        var pending = refund(p, "REF-C1-18700-P", null, org.de013.paymentservice.entity.enums.RefundStatus.PENDING);
        var old = refund(p, "REF-C1-18700-F", "re_c1_old", org.de013.paymentservice.entity.enums.RefundStatus.FAILED);
        var r = new CompensationProofStore.Request(java.util.UUID.randomUUID(), 1, p.getId(), p.getPaymentNumber(), p.getAmount(), "USD", null);
        var claim = proofStore.begin(18700, r);
        var request = org.de013.paymentservice.dto.refund.RefundRequest.builder().paymentId(p.getId()).amount(new BigDecimal("5.00")).reason("requested_by_customer").build();
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> refunds.createRefund(request)).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> refunds.processRefund(pending.getId())).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> refunds.approveRefund(pending.getId(), "admin")).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> refunds.cancelRefund(pending.getId(), "no")).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> refunds.rejectRefund(pending.getId(), "admin", "no")).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> refunds.updateRefundStatus(pending.getId(), org.de013.paymentservice.entity.enums.RefundStatus.FAILED, "no")).getStatusCode().value());
        refunds.cleanupOldFailedRefunds(java.time.LocalDateTime.now().plusDays(1));
        assertTrue(refundRepository.existsById(old.getId()));
        verify(stripe, never()).createRefund(any());
        var evidence = new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.Evidence("acct_test", false, java.time.Instant.now(), java.util.List.of(
                new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.ChargeEvidence("ch_c1_18700", "pi_c1_18700", 2500, "succeeded", java.util.List.of(
                        new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.RefundEvidence("re_c1_18700", 2500, "succeeded")))));
        assertEquals("COMPENSATED", proofStore.complete(18700, r.operationId(), claim.token(), new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.Result(true, null, evidence)).status());
    }

    @Test void proofClaimRejectsLateRefundReadbackFacts() throws Exception {
        var p = intent(18701, "pi_c1_18701", org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, null);
        var pending = refund(p, "REF-C1-18701", "re_c1_18701", org.de013.paymentservice.entity.enums.RefundStatus.PENDING);
        var r = new CompensationProofStore.Request(java.util.UUID.randomUUID(), 1, p.getId(), p.getPaymentNumber(), p.getAmount(), "USD", null);
        var claim = proofStore.begin(18701, r);
        when(factory.getStripeGateway()).thenReturn(stripe);
        when(stripe.getRefundResponse("re_c1_18701")).thenReturn(org.de013.paymentservice.dto.stripe.StripeRefundResponse.builder().refundId("re_c1_18701").status("succeeded").build());
        refunds.syncRefundStatusWithStripe(pending.getId());
        assertEquals(org.de013.paymentservice.entity.enums.RefundStatus.SUCCEEDED, refundRepository.findById(pending.getId()).orElseThrow().getStatus());
        assertEquals(claim.view().financialVersion() + 1, jdbc.queryForObject("select financial_version from payment_order_guard where order_id=18701", Long.class));
        var result = proofStore.complete(18701, r.operationId(), claim.token(), c1Proof("pi_c1_18701"));
        assertEquals("HOLD", result.status()); assertEquals("FINANCIAL_FACTS_CHANGED", result.reason());
    }

    @Test void proofClaimBlocksReservationHostedRetryAndDirectPaymentMutation() {
        var p = intent(18702, "pi_c1_18702", org.de013.paymentservice.entity.enums.PaymentStatus.FAILED, null);
        p.setBlocking(false); p.setPaymentNumber("C1-FAILED-18702"); repository.saveAndFlush(p);
        var candidate = intent(18702, "pi_c1_candidate_18702", org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, null);
        candidate.setStripeIntentCreatedByService(true); candidate.setStripeIntentLivemode(false); repository.saveAndFlush(candidate);
        var r = new CompensationProofStore.Request(java.util.UUID.randomUUID(), 1, candidate.getId(), candidate.getPaymentNumber(), candidate.getAmount(), "USD", null);
        var claim = proofStore.begin(18702, r);
        var newPayment = Payment.builder().orderId(18702L).userId("owner").paymentNumber("C1-SECOND").amount(new BigDecimal("25.00")).currency(Currency.USD).method(PaymentMethodType.CARD).status(org.de013.paymentservice.entity.enums.PaymentStatus.PENDING).gatewayName("STRIPE").build();
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> reservation.reserve(newPayment)).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> hostedStore.reserve(18702L, "owner", new BigDecimal("25.00"), "http://fixture.test")).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> retryAttempts.claim(p.getId(), "c1-retry", "pm_c1", p.getAmount(), "USD")).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> payments.cancelPayment(p.getId(), "no")).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> payments.updatePaymentStatus(p.getId(), org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, "no")).getStatusCode().value());
        assertEquals(0, jdbc.queryForObject("select count(*) from payment_retry_attempt where payment_id=?", Integer.class, p.getId()));
        assertEquals(2, jdbc.queryForObject("select count(*) from payments where order_id=18702", Integer.class));
        assertEquals(claim.view().financialVersion(), jdbc.queryForObject("select financial_version from payment_order_guard where order_id=18702", Long.class));
        verifyNoInteractions(factory, hostedStripe);
        assertEquals("COMPENSATED", proofStore.complete(18702, r.operationId(), claim.token(), c1Proof("pi_c1_candidate_18702")).status());
    }

    private org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.Result c1Proof(String intent) {
        var evidence = new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.Evidence("acct_test", false, java.time.Instant.now(), java.util.List.of(
                new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.ChargeEvidence("ch_" + intent, intent, 2500, "succeeded", java.util.List.of(
                        new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.RefundEvidence("re_" + intent, 2500, "succeeded")))));
        return new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.Result(true, null, evidence);
    }

    @Test void proofClaimDetectsSignedIntentAndKnownChargeAndUnknownDispute() throws Exception {
        for (int i = 0; i < 3; i++) {
            long order = 18800 + i; String pi = "pi_ingest_" + i;
            var p = intent(order, pi, i == 0 ? org.de013.paymentservice.entity.enums.PaymentStatus.PENDING : org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, null);
            p.setStripeIntentCreatedByService(true); p.setStripeIntentLivemode(false); repository.saveAndFlush(p);
            var r = new CompensationProofStore.Request(java.util.UUID.randomUUID(), 1, p.getId(), p.getPaymentNumber(), p.getAmount(), "USD", null);
            var claim = proofStore.begin(order, r);
            String body = switch (i) {
                case 0 -> "{\"id\":\"evt_c1_intent\",\"type\":\"payment_intent.succeeded\",\"livemode\":false,\"data\":{\"object\":{\"object\":\"payment_intent\",\"id\":\"" + pi + "\",\"status\":\"succeeded\",\"amount\":2500,\"currency\":\"usd\",\"latest_charge\":\"ch_c1_intent\"}}}";
                case 1 -> "{\"id\":\"evt_c1_charge\",\"type\":\"charge.succeeded\",\"livemode\":false,\"data\":{\"object\":{\"object\":\"charge\",\"id\":\"ch_c1_known\",\"payment_intent\":\"" + pi + "\",\"status\":\"succeeded\",\"amount\":2500,\"currency\":\"usd\"}}}";
                default -> "{\"id\":\"evt_c1_unknown\",\"type\":\"charge.dispute.created\",\"livemode\":false,\"data\":{\"object\":{\"object\":\"dispute\",\"id\":\"dp_c1_unknown\",\"charge\":\"ch_unknown\"}}}";
            };
            refundHttp(body);
            long version = jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, order);
            if (i == 2) assertEquals(claim.view().financialVersion(), version); else assertTrue(version > claim.view().financialVersion());
            if (i == 0) assertEquals(org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, repository.findById(p.getId()).orElseThrow().getStatus());
            assertTrue(jdbc.queryForObject("select permanent_fence from payment_order_guard where order_id=?", Boolean.class, order));
            var result = proofStore.complete(order, r.operationId(), claim.token(), c1Proof(pi));
            assertEquals("HOLD", result.status()); assertEquals(i == 2 ? "UNRESOLVED_FINANCIAL_OBSERVATION" : "FINANCIAL_FACTS_CHANGED", result.reason());
            if (i == 2) jdbc.update("delete from stripe_financial_observation where event_id='evt_c1_unknown'");
        }
    }

    @Test void proofClaimDetectsSignedVnpaySuccessAndFailure() {
        when(vnpayGateway.verifyCallback(anyMap())).thenReturn(true);
        for (int i = 0; i < 2; i++) {
            long order = 18810 + i; String pi = "pi_vnp_candidate_" + i;
            var p = intent(order, pi, org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, null);
            p.setStripeIntentCreatedByService(true); p.setStripeIntentLivemode(false); p.setBlocking(false); repository.saveAndFlush(p);
            var v = repository.saveAndFlush(Payment.builder().orderId(order).userId("owner").paymentNumber("VNP-C1-" + i).amount(new BigDecimal("25.00"))
                    .currency(Currency.USD).method(PaymentMethodType.WALLET).status(org.de013.paymentservice.entity.enums.PaymentStatus.PENDING).gatewayName("VNPAY").providerAmountVnd(100L).providerCurrency("VND").build());
            var r = new CompensationProofStore.Request(java.util.UUID.randomUUID(), 1, p.getId(), p.getPaymentNumber(), p.getAmount(), "USD", null);
            var claim = proofStore.begin(order, r);
            var fields = new java.util.HashMap<String, String>(); fields.put("vnp_TmnCode", gatewayConfig.getGateways().getVnpay().getTmnCode());
            fields.put("vnp_TxnRef", v.getPaymentNumber()); fields.put("vnp_Amount", "10000"); fields.put("vnp_ResponseCode", i == 0 ? "00" : "01"); fields.put("vnp_TransactionStatus", i == 0 ? "00" : "01");
            assertEquals("00", ipn.process(fields).get("RspCode"));
            assertTrue(jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, order) > claim.view().financialVersion());
            assertTrue(jdbc.queryForObject("select permanent_fence from payment_order_guard where order_id=?", Boolean.class, order));
            var result = proofStore.complete(order, r.operationId(), claim.token(), c1Proof(pi));
            assertEquals("HOLD", result.status()); assertEquals("FINANCIAL_FACTS_CHANGED", result.reason());
        }
    }

    @Test void proofClaimDetectsLateHostedRecord() {
        long order = 18820;
        var hostedPayment = hostedStore.reserve(order, "owner", new BigDecimal("25.00"), "https://fixture.test");
        hostedStore.record(order, 0, "cs_c1_18820", "OPEN", null);
        jdbc.update("update payments set blocking=false where id=?", hostedPayment.getPaymentId());
        var candidate = intent(order, "pi_c1_hosted_candidate", org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, null);
        candidate.setStripeIntentCreatedByService(true); candidate.setStripeIntentLivemode(false); repository.saveAndFlush(candidate);
        var r = new CompensationProofStore.Request(java.util.UUID.randomUUID(), 1, candidate.getId(), candidate.getPaymentNumber(), candidate.getAmount(), "USD", null);
        var claim = proofStore.begin(order, r);
        hostedStore.record(order, 0, "cs_c1_18820", "PAID", "pi_c1_hosted");
        assertEquals("PAID", jdbc.queryForObject("select session_state from hosted_checkouts where order_id=?", String.class, order));
        assertTrue(jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, order) > claim.view().financialVersion());
        assertTrue(jdbc.queryForObject("select permanent_fence from payment_order_guard where order_id=?", Boolean.class, order));
        var result = proofStore.complete(order, r.operationId(), claim.token(), c1Proof("pi_c1_hosted_candidate"));
        assertEquals("HOLD", result.status()); assertEquals("FINANCIAL_FACTS_CHANGED", result.reason());
    }

    @Test void proofClaimDetectsProviderReadbackAndLateClosureContradiction() throws Exception {
        for (int i = 0; i < 2; i++) {
            long order = 18830 + i; String pi = "pi_readback_c1_" + i;
            var p = intent(order, pi, i == 0 ? org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED : org.de013.paymentservice.entity.enums.PaymentStatus.PENDING, null);
            p.setStripeIntentCreatedByService(true); p.setStripeIntentLivemode(false); repository.saveAndFlush(p);
            java.util.UUID close = null;
            if (i == 1) {
                close = java.util.UUID.randomUUID();
                jdbc.update("insert into payment_order_guard(order_id,close_operation_id,close_status,close_safe_proof,close_safe_at) values (?,?,'SAFE','fixture',clock_timestamp())", order, close);
            }
            var r = new CompensationProofStore.Request(java.util.UUID.randomUUID(), 1, p.getId(), p.getPaymentNumber(), p.getAmount(), "USD", close);
            var claim = proofStore.begin(order, r);
            if (i == 0) {
                when(factory.getGateway("STRIPE")).thenReturn(stripe);
                when(stripe.getPaymentIntent(pi)).thenReturn(StripePaymentResponse.builder().paymentIntentId(pi).status("processing").amount(new BigDecimal("25.00")).build());
                payments.syncPaymentStatusWithStripe(p.getId());
            } else {
                String body = "{\"id\":\"evt_c1_late_closure\",\"type\":\"payment_intent.succeeded\",\"livemode\":false,\"data\":{\"object\":{\"object\":\"payment_intent\",\"id\":\"" + pi + "\",\"status\":\"succeeded\",\"amount\":2500,\"currency\":\"usd\",\"latest_charge\":\"ch_c1_late\"}}}";
                refundHttp(body);
                assertEquals("HOLD", jdbc.queryForObject("select close_status from payment_order_guard where order_id=?", String.class, order));
                assertEquals("LATE_SUCCESS", jdbc.queryForObject("select close_reason from payment_order_guard where order_id=?", String.class, order));
            }
            assertTrue(jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, order) > claim.view().financialVersion());
            assertTrue(jdbc.queryForObject("select permanent_fence from payment_order_guard where order_id=?", Boolean.class, order));
            var result = proofStore.complete(order, r.operationId(), claim.token(), c1Proof(pi));
            assertEquals("HOLD", result.status()); assertEquals("FINANCIAL_FACTS_CHANGED", result.reason());
        }
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
        assertEquals(org.de013.paymentservice.entity.enums.RefundStatus.FAILED, refundRepository.findById(failed.getId()).orElseThrow().getStatus());
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

    @Test void fencedRefundIngestionVersionsOnlyChangedFacts() throws Exception {
        var payment = intent(18151, "pi_refund_18151", org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, null);
        var pending = refund(payment, "REF-18151-A", "re_18151_a", org.de013.paymentservice.entity.enums.RefundStatus.PENDING);
        var other = refund(payment, "REF-18151-B", "re_18151_b", org.de013.paymentservice.entity.enums.RefundStatus.PENDING);
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(s -> guard.permanentFence(18151L));
        assertThrows(ResponseStatusException.class, () -> refunds.cancelRefund(other.getId(), "no"));
        assertThrows(ResponseStatusException.class, () -> refunds.rejectRefund(other.getId(), "admin", "no"));
        assertThrows(ResponseStatusException.class, () -> refunds.updateRefundStatus(other.getId(), org.de013.paymentservice.entity.enums.RefundStatus.FAILED, "no"));
        when(factory.getStripeGateway()).thenReturn(stripe);
        when(stripe.getRefundResponse("re_18151_a")).thenReturn(org.de013.paymentservice.dto.stripe.StripeRefundResponse.builder().refundId("re_18151_a").status("succeeded").build());
        long before = jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, 18151L);
        refunds.syncRefundStatusWithStripe(pending.getId());
        assertEquals(before + 1, jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, 18151L));
        refunds.syncRefundStatusWithStripe(pending.getId());
        assertEquals(before + 1, jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, 18151L));
        send("evt_refund_18151_a", "refund.updated", "refund", "re_18151_b", "succeeded");
        assertEquals(before + 2, jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, 18151L));
        send("evt_refund_18151_b", "refund.updated", "refund", "re_18151_b", "succeeded");
        assertEquals(before + 3, jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, 18151L));
    }

    @Test void refundCleanupVersionsOnlyActualDeletion() {
        var payment = intent(18152, "pi_refund_18152", org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED, null);
        var old = refund(payment, "REF-18152", "re_18152", org.de013.paymentservice.entity.enums.RefundStatus.FAILED);
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(s -> guard.lock(18152L));
        long before = jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, 18152L);
        refunds.cleanupOldFailedRefunds(java.time.LocalDateTime.now().plusDays(1));
        assertFalse(refundRepository.existsById(old.getId()));
        assertEquals(before + 1, jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, 18152L));
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
