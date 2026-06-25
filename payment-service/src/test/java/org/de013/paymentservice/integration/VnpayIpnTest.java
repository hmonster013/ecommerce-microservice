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
import java.util.List;
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
@SpringBootTest
@ActiveProfiles("test")
class VnpayIpnTest {

    @Autowired
    private WebhookController webhookController;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private ProcessedStripeEventRepository processedStripeEventRepository;
    @Autowired
    private org.de013.paymentservice.repository.OutboxEventRepository outboxEventRepository;
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
        outboxEventRepository.deleteAll();
    }

    @Test
    void ipn_validSignatureAndSuccessCode_marksPaymentSucceededAndOrderPaid() {
        String txnRef = "PMT-IPN-001";
        seedPendingPayment(txnRef, new BigDecimal("10.00"));
        Map<String, String> params = signedIpn(txnRef, toVnpAmount(new BigDecimal("10.00")), "00");

        ResponseEntity<Map<String, String>> resp = webhookController.handleVnpayIpn(params);

        assertEquals("00", resp.getBody().get("RspCode"));
        assertEquals(PaymentStatus.SUCCEEDED,
                paymentRepository.findByPaymentNumber(txnRef).orElseThrow().getStatus());
        verify(orderServiceClient, times(1)).markOrderAsPaid(eq(1L), anyLong(), eq(txnRef));
        assertTrue(processedStripeEventRepository.existsById("vnpay_" + txnRef));

        // Verify outbox event is created
        List<org.de013.paymentservice.entity.OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertEquals(1, outboxEvents.size());
        org.de013.paymentservice.entity.OutboxEvent outboxEvent = outboxEvents.get(0);
        assertEquals(txnRef, outboxEvent.getAggregateId());
        assertEquals("PAYMENT", outboxEvent.getAggregateType());
        assertEquals("payment.succeeded", outboxEvent.getEventType());
        assertEquals(org.de013.paymentservice.entity.enums.OutboxStatus.PENDING, outboxEvent.getStatus());
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

        // Verify ONLY one outbox event is created
        List<org.de013.paymentservice.entity.OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertEquals(1, outboxEvents.size());
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

    @Test
    void ipn_failureCode_marksPaymentFailed_andWritesToOutbox() {
        String txnRef = "PMT-IPN-005";
        seedPendingPayment(txnRef, new BigDecimal("10.00"));
        // VNPay IPN with failure code (e.g. 01 - Transaction incomplete)
        Map<String, String> params = signedIpn(txnRef, toVnpAmount(new BigDecimal("10.00")), "01");

        ResponseEntity<Map<String, String>> resp = webhookController.handleVnpayIpn(params);

        assertEquals("00", resp.getBody().get("RspCode"));
        assertEquals(PaymentStatus.FAILED,
                paymentRepository.findByPaymentNumber(txnRef).orElseThrow().getStatus());
        verify(orderServiceClient, times(1)).markOrderPaymentFailed(eq(1L), anyString());

        // Verify outbox event is created for failure
        List<org.de013.paymentservice.entity.OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertEquals(1, outboxEvents.size());
        org.de013.paymentservice.entity.OutboxEvent outboxEvent = outboxEvents.get(0);
        assertEquals(txnRef, outboxEvent.getAggregateId());
        assertEquals("PAYMENT", outboxEvent.getAggregateType());
        assertEquals("payment.failed", outboxEvent.getEventType());
        assertEquals(org.de013.paymentservice.entity.enums.OutboxStatus.PENDING, outboxEvent.getStatus());
    }

    // ----- helpers -----

    private void seedPendingPayment(String txnRef, BigDecimal amountUsd) {
        Payment payment = Payment.builder()
                .paymentNumber(txnRef)
                .orderId(1L)
                .userId("user-1")
                .amount(amountUsd)
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
