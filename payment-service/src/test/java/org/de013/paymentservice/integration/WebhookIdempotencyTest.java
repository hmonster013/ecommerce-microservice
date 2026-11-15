package org.de013.paymentservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.de013.paymentservice.dto.payment.StripeWebhookRequest;
import org.de013.paymentservice.entity.ProcessedStripeEvent;
import org.de013.paymentservice.repository.ProcessedStripeEventRepository;
import org.de013.paymentservice.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@org.testcontainers.junit.jupiter.Testcontainers
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"})
@ActiveProfiles("test")
@Transactional
class WebhookIdempotencyTest {
    @org.testcontainers.junit.jupiter.Container static final org.testcontainers.containers.PostgreSQLContainer<?> DB = new org.testcontainers.containers.PostgreSQLContainer<>("postgres:17-alpine");
    @org.springframework.test.context.DynamicPropertySource static void database(org.springframework.test.context.DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", DB::getJdbcUrl);
        p.add("spring.datasource.username", DB::getUsername);
        p.add("spring.datasource.password", DB::getPassword);
        p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
    @Autowired private org.de013.paymentservice.repository.PaymentRepository payments;

    @Autowired
    private WebhookService webhookService;

    @Autowired
    private ProcessedStripeEventRepository processedStripeEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        processedStripeEventRepository.deleteAll();
    }

    @Test
    void processStripeWebhook_WhenReceivedTwice_ShouldProcessOnce() throws Exception {
        // Construct mock webhook request payload
        Map<String, Object> paymentIntentObj = new HashMap<>();
        paymentIntentObj.put("id", "pi_mock_123");
        paymentIntentObj.put("object", "payment_intent");
        paymentIntentObj.put("status", "succeeded");
        paymentIntentObj.put("amount", 10000L); // $100.00
        paymentIntentObj.put("currency", "usd");

        StripeWebhookRequest.WebhookData data = StripeWebhookRequest.WebhookData.builder()
                .object(paymentIntentObj)
                .build();

        StripeWebhookRequest webhookRequest = StripeWebhookRequest.builder()
                .id("evt_test_123") // Idempotency Key
                .object("event")
                .type("payment_intent.succeeded")
                .data(data)
                .build();

        String payload = objectMapper.writeValueAsString(webhookRequest);
        long timestamp = java.time.Instant.now().getEpochSecond();
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(webhookService.getWebhookSecret()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "t=" + timestamp + ",v1=" + java.util.HexFormat.of().formatHex(
                mac.doFinal((timestamp + "." + payload).getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        payments.saveAndFlush(org.de013.paymentservice.entity.Payment.builder().paymentNumber("PAY-IDEMPOTENCY")
                .orderId(77901L).userId("owner").amount(new java.math.BigDecimal("100.00"))
                .currency(org.de013.paymentservice.entity.enums.Currency.USD)
                .method(org.de013.paymentservice.entity.enums.PaymentMethodType.CARD)
                .status(org.de013.paymentservice.entity.enums.PaymentStatus.PENDING).gatewayName("STRIPE")
                .stripePaymentIntentId("pi_mock_123").build());
        // First call
        webhookService.processStripeWebhook(payload, signature);

        // Assert that the event is processed and saved in db
        assertEquals(1, processedStripeEventRepository.count());
        assertTrue(processedStripeEventRepository.existsById("evt_test_123"));

        // Second call with same event ID
        webhookService.processStripeWebhook(payload, signature);

        // Assert that the count is STILL 1 (Idempotent!)
        assertEquals(1, processedStripeEventRepository.count());
    }

    @Test
    void testSignatureMustNotBypassProductionVerification() {
        assertThrows(org.de013.paymentservice.exception.PaymentGatewayException.class,
                () -> webhookService.processStripeWebhook("{}", "test_signature"));
        assertEquals(0, processedStripeEventRepository.count());
    }
}
