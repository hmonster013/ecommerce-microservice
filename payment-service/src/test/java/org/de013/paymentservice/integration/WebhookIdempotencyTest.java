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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WebhookIdempotencyTest {

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
        String signature = "test_signature"; // Bypasses Stripe verification signature check

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
}
