package org.de013.paymentservice.integration;

import org.de013.paymentservice.client.NotificationServiceClient;
import org.de013.paymentservice.client.OrderServiceClient;
import org.de013.paymentservice.client.UserServiceClient;
import org.de013.paymentservice.config.PaymentGatewayConfig;
import org.de013.paymentservice.controller.WebhookController;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.enums.Currency;
import org.de013.paymentservice.entity.enums.PaymentMethodType;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.de013.paymentservice.gateway.vnpay.VnpayUtils;
import org.de013.paymentservice.repository.PaymentRepository;
import org.de013.paymentservice.repository.ProcessedStripeEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for the VNPay IPN (server-to-server) webhook.
 *
 * Hermetic: the test signs fake IPN callbacks with the same fixed hash secret
 * the app is configured with in the {@code test} profile, so it never touches
 * the real VNPay sandbox account. Swapping real credentials (in .env) never
 * requires changing these tests.
 */
@org.testcontainers.junit.jupiter.Testcontainers
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"})
@ActiveProfiles("test")
class VnpayIpnTest {
    @org.testcontainers.junit.jupiter.Container static final org.testcontainers.containers.PostgreSQLContainer<?> DB = new org.testcontainers.containers.PostgreSQLContainer<>("postgres:17-alpine");
    @org.springframework.test.context.DynamicPropertySource static void database(org.springframework.test.context.DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", DB::getJdbcUrl);
        p.add("spring.datasource.username", DB::getUsername);
        p.add("spring.datasource.password", DB::getPassword);
        p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired private org.de013.paymentservice.service.VnpayIpnService ipnService;

    @Autowired
    private WebhookController webhookController;
    @Autowired
    private PaymentRepository paymentRepository;
    @org.springframework.boot.test.mock.mockito.SpyBean
    private ProcessedStripeEventRepository processedStripeEventRepository;
    @Autowired
    private PaymentGatewayConfig config;

    @MockBean
    private OrderServiceClient orderServiceClient;
    @MockBean
    private NotificationServiceClient notificationServiceClient;
    @MockBean
    private UserServiceClient userServiceClient;

    private String secret;
    private String tmnCode;
    private long vndRate;

    @BeforeEach
    void setUp() {
        PaymentGatewayConfig.Vnpay vnpay = config.getGateways().getVnpay();
        secret = vnpay.getHashSecret();
        tmnCode = vnpay.getTmnCode();
        vndRate = vnpay.getVndRate();
        processedStripeEventRepository.deleteAll();
        paymentRepository.deleteAll();
        jdbc.update("delete from payment_order_guard");
    }

    @Test
    void ipn_validSignatureAndSuccessCode_marksPaymentSucceededAndOrderPaid() {
        String txnRef = "PMT-IPN-001";
        seedPendingPayment(txnRef, new BigDecimal("10.00"));
        var legacy = paymentRepository.findByPaymentNumber(txnRef).orElseThrow();
        assertNull(legacy.getProviderExchangeRate());
        assertNull(legacy.getProviderQuotedAt());
        Map<String, String> params = signedIpn(txnRef, toVnpAmount(new BigDecimal("10.00")), "00");

        ResponseEntity<Map<String, String>> resp = webhookController.handleVnpayIpn(params);

        assertEquals("00", resp.getBody().get("RspCode"));
        assertEquals(PaymentStatus.SUCCEEDED,
                paymentRepository.findByPaymentNumber(txnRef).orElseThrow().getStatus());
        verify(orderServiceClient, times(1)).markOrderAsPaid(eq(1L), anyLong(), eq(txnRef));
        assertTrue(processedStripeEventRepository.existsById("vnpay_" + txnRef));
    }

    @Test
    void ipn_sentTwice_isIdempotent() {
        String txnRef = "PMT-IPN-002";
        seedPendingPayment(txnRef, new BigDecimal("10.00"));
        Map<String, String> params = signedIpn(txnRef, toVnpAmount(new BigDecimal("10.00")), "00");

        webhookController.handleVnpayIpn(params);
        ResponseEntity<Map<String, String>> second = webhookController.handleVnpayIpn(params);

        assertEquals("02", second.getBody().get("RspCode"));
        // Order must be marked paid exactly once across the two callbacks
        verify(orderServiceClient, times(1)).markOrderAsPaid(eq(1L), anyLong(), eq(txnRef));
    }

    @Test
    void ipn_invalidSignature_returns97_andDoesNotUpdate() {
        String txnRef = "PMT-IPN-003";
        seedPendingPayment(txnRef, new BigDecimal("10.00"));
        Map<String, String> params = signedIpn(txnRef, toVnpAmount(new BigDecimal("10.00")), "00");
        params.put("vnp_SecureHash", "deadbeef");

        ResponseEntity<Map<String, String>> resp = webhookController.handleVnpayIpn(params);

        assertEquals("97", resp.getBody().get("RspCode"));
        assertEquals(PaymentStatus.PENDING,
                paymentRepository.findByPaymentNumber(txnRef).orElseThrow().getStatus());
        verify(orderServiceClient, never()).markOrderAsPaid(anyLong(), anyLong(), anyString());
    }

    @Test
    void ipn_amountMismatch_returns04_andDoesNotUpdate() {
        String txnRef = "PMT-IPN-004";
        seedPendingPayment(txnRef, new BigDecimal("10.00"));
        // Correctly signed, but the amount does not match the stored payment
        Map<String, String> params = signedIpn(txnRef, 999L * 100, "00");

        ResponseEntity<Map<String, String>> resp = webhookController.handleVnpayIpn(params);

        assertEquals("04", resp.getBody().get("RspCode"));
        assertEquals(PaymentStatus.PENDING,
                paymentRepository.findByPaymentNumber(txnRef).orElseThrow().getStatus());
        verify(orderServiceClient, never()).markOrderAsPaid(anyLong(), anyLong(), anyString());
    }

    @Test void eventFlushFailureRollsBackPaymentAndAllowsReplay() {
        String ref = "PMT-IPN-ROLLBACK";
        seedPendingPayment(ref, new BigDecimal("10.00"));
        var callback = signedIpn(ref, toVnpAmount(new BigDecimal("10.00")), "00");
        doThrow(new IllegalStateException("simulated event flush failure"))
                .when(processedStripeEventRepository).flush();
        var failure = assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class, () -> ipnService.process(callback));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals(PaymentStatus.PENDING, paymentRepository.findByPaymentNumber(ref).orElseThrow().getStatus());
        assertFalse(processedStripeEventRepository.existsById("vnpay_" + ref));
        verify(orderServiceClient, never()).markOrderAsPaid(anyLong(), anyLong(), anyString());
        reset(processedStripeEventRepository);
        assertEquals("00", ipnService.process(callback).get("RspCode"));
        assertEquals(PaymentStatus.SUCCEEDED, paymentRepository.findByPaymentNumber(ref).orElseThrow().getStatus());
        assertTrue(processedStripeEventRepository.existsById("vnpay_" + ref));
    }

    @Test void signedSuccessUnderHoldPreservesEvidenceAndSuppressesDispatch() {
        String ref = "PMT-IPN-HOLD";
        seedPendingPayment(ref, new BigDecimal("10.00"));
        jdbc.update("insert into payment_order_guard (order_id, reconciliation_hold) values (1, true)");
        var callback = signedIpn(ref, toVnpAmount(new BigDecimal("10.00")), "00");
        assertEquals("00", webhookController.handleVnpayIpn(callback).getBody().get("RspCode"));
        assertEquals(PaymentStatus.SUCCEEDED, paymentRepository.findByPaymentNumber(ref).orElseThrow().getStatus());
        assertTrue(processedStripeEventRepository.existsById("vnpay_" + ref));
        assertEquals("02", webhookController.handleVnpayIpn(callback).getBody().get("RspCode"));
        assertEquals("02", webhookController.handleVnpayIpn(signedIpn(ref, toVnpAmount(new BigDecimal("10.00")), "01")).getBody().get("RspCode"));
        assertEquals(PaymentStatus.SUCCEEDED, paymentRepository.findByPaymentNumber(ref).orElseThrow().getStatus());
        verifyNoInteractions(orderServiceClient, notificationServiceClient, userServiceClient);
    }

    @Test void directUnsignedServiceCallCannotSettle() {
        String ref = "PMT-IPN-DIRECT";
        seedPendingPayment(ref, new BigDecimal("10.00"));
        var callback = signedIpn(ref, toVnpAmount(new BigDecimal("10.00")), "00");
        callback.put("vnp_SecureHash", "invalid");
        assertEquals("97", ipnService.process(callback).get("RspCode"));
        assertEquals(PaymentStatus.PENDING, paymentRepository.findByPaymentNumber(ref).orElseThrow().getStatus());
        assertFalse(processedStripeEventRepository.existsById("vnpay_" + ref));
    }

    @Test void legacySnapshotCommitsHoldAndRetries() {
        String ref = "PMT-LEGACY";
        seedPendingPayment(ref, BigDecimal.TEN);
        jdbc.update("update payments set provider_amount_vnd = null, provider_currency = null where payment_number = ?", ref);
        var callback = signedIpn(ref, toVnpAmount(BigDecimal.TEN), "00");
        for (int i = 0; i < 2; i++) {
            assertEquals("99", ipnService.process(callback).get("RspCode"));
            assertTrue(jdbc.queryForObject("select reconciliation_hold from payment_order_guard where order_id = 1", Boolean.class));
        }
        assertEquals(PaymentStatus.PENDING, paymentRepository.findByPaymentNumber(ref).orElseThrow().getStatus());
        assertFalse(processedStripeEventRepository.existsById("vnpay_" + ref));
        verifyNoInteractions(orderServiceClient, notificationServiceClient, userServiceClient);
    }

    @Test void validatesWireAndMerchantBeforeDuplicate() {
        String ref = "PMT-WIRE";
        seedPendingPayment(ref, BigDecimal.TEN);
        long wire = toVnpAmount(BigDecimal.TEN);
        assertEquals("00", ipnService.process(signedIpn(ref, wire, "00")).get("RspCode"));
        for (String value : new String[]{Long.toString(wire - 1), Long.toString(wire + 1), "-1", "1.0", "999999999999999999999", ""}) {
            var callback = signedIpn(ref, wire, "00");
            callback.put("vnp_Amount", value);
            assertEquals("04", ipnService.process(resign(callback)).get("RspCode"));
        }
        for (String merchant : new String[]{null, "", "wrong"}) {
            var callback = signedIpn(ref, wire, "00");
            if (merchant == null) callback.remove("vnp_TmnCode"); else callback.put("vnp_TmnCode", merchant);
            assertEquals("97", ipnService.process(resign(callback)).get("RspCode"));
        }
    }

    @Test void missingAndMalformedAmountsNeverSettle() {
        String ref = "PMT-BAD-AMOUNT";
        seedPendingPayment(ref, BigDecimal.TEN);
        for (String value : new String[]{null, "", "+1", "-1", "1.0", "1 0", "999999999999999999999"}) {
            var callback = signedIpn(ref, toVnpAmount(BigDecimal.TEN), "00");
            if (value == null) callback.remove("vnp_Amount"); else callback.put("vnp_Amount", value);
            assertEquals("04", ipnService.process(resign(callback)).get("RspCode"));
            assertEquals(PaymentStatus.PENDING, paymentRepository.findByPaymentNumber(ref).orElseThrow().getStatus());
            assertFalse(processedStripeEventRepository.existsById("vnpay_" + ref));
        }
        verifyNoInteractions(orderServiceClient, notificationServiceClient, userServiceClient);
    }

    @Test void responseAndTransactionStatusMatrixUnderNormalAndHeldGuard() {
        int index = 0;
        for (boolean held : new boolean[]{false, true}) for (String response : new String[]{"00", "01", null}) for (String status : new String[]{"00", "01", null}) {
            processedStripeEventRepository.deleteAll(); paymentRepository.deleteAll(); jdbc.update("delete from payment_order_guard");
            reset(orderServiceClient, notificationServiceClient, userServiceClient);
            String ref = "PMT-MATRIX-" + index++;
            seedPendingPayment(ref, BigDecimal.TEN);
            if (held) jdbc.update("insert into payment_order_guard (order_id, reconciliation_hold) values (1, true)");
            var callback = signedIpn(ref, toVnpAmount(BigDecimal.TEN), "00");
            if (response == null) callback.remove("vnp_ResponseCode"); else callback.put("vnp_ResponseCode", response);
            if (status == null) callback.remove("vnp_TransactionStatus"); else callback.put("vnp_TransactionStatus", status);
            assertEquals("00", ipnService.process(resign(callback)).get("RspCode"));
            boolean success = "00".equals(response) && "00".equals(status);
            assertEquals(success ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED, paymentRepository.findByPaymentNumber(ref).orElseThrow().getStatus());
            assertEquals(success, processedStripeEventRepository.existsById("vnpay_" + ref));
            verify(orderServiceClient, times(success && !held ? 1 : 0)).markOrderAsPaid(anyLong(), anyLong(), anyString());
            verify(notificationServiceClient, times(success && !held ? 1 : 0)).sendEmail(anyMap());
            if (held) verifyNoInteractions(orderServiceClient, notificationServiceClient, userServiceClient);
        }
    }

    private Map<String, String> resign(Map<String, String> callback) {
        SortedMap<String, String> fields = new TreeMap<>(callback);
        fields.remove("vnp_SecureHash");
        fields.values().removeIf(v -> v == null || v.isEmpty());
        callback.put("vnp_SecureHash", VnpayUtils.hmacSHA512(secret, VnpayUtils.buildHashData(fields)));
        return callback;
    }

    // ----- helpers -----

    private void seedPendingPayment(String txnRef, BigDecimal amountUsd) {
        Payment payment = Payment.builder()
                .paymentNumber(txnRef)
                .orderId(1L)
                .userId("user-1")
                .amount(amountUsd)
                .providerAmountVnd(Math.round(amountUsd.doubleValue() * vndRate))
                .providerCurrency("VND")
                .currency(Currency.USD)
                .status(PaymentStatus.PENDING)
                .method(PaymentMethodType.WALLET)
                .gatewayName("VNPAY")
                .gatewayTxnRef(txnRef)
                .receiptEmail("buyer@example.com")
                .createdBy("TEST")
                .updatedBy("TEST")
                .build();
        paymentRepository.save(payment);
    }

    /** Builds an IPN parameter map signed with the test hash secret. */
    private Map<String, String> signedIpn(String txnRef, long vnpAmount, String responseCode) {
        SortedMap<String, String> fields = new TreeMap<>();
        fields.put("vnp_Amount", String.valueOf(vnpAmount));
        fields.put("vnp_TmnCode", tmnCode);
        fields.put("vnp_TxnRef", txnRef);
        fields.put("vnp_ResponseCode", responseCode);
        fields.put("vnp_TransactionStatus", responseCode);
        fields.put("vnp_OrderInfo", "Thanh toan don hang #1");

        String hashData = VnpayUtils.buildHashData(fields);
        String secureHash = VnpayUtils.hmacSHA512(secret, hashData);

        Map<String, String> params = new HashMap<>(fields);
        params.put("vnp_SecureHash", secureHash);
        return params;
    }

    /** Mirrors VnpayService: vnp_Amount = round(amountUsd * vndRate) * 100. */
    private long toVnpAmount(BigDecimal amountUsd) {
        long amountVnd = Math.round(amountUsd.doubleValue() * vndRate);
        return amountVnd * 100;
    }
}
