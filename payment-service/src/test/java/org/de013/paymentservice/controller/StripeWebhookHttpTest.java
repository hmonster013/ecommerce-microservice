package org.de013.paymentservice.controller;

import org.de013.paymentservice.gateway.stripe.StripeRetryVerifier;

import org.de013.paymentservice.service.PaymentRetryAttempts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.de013.paymentservice.client.*;
import org.de013.paymentservice.config.PaymentGatewayConfig;
import org.de013.paymentservice.exception.PaymentExceptionHandler;
import org.de013.paymentservice.gateway.PaymentGatewayFactory;
import org.de013.paymentservice.gateway.stripe.StripeWebhookService;
import org.de013.paymentservice.gateway.vnpay.VnpayPaymentGateway;
import org.de013.paymentservice.repository.*;
import org.de013.paymentservice.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StripeWebhookHttpTest {
    private static final String SECRET = "fixture_secret";
    private static final String BODY = "{\"id\":\"evt_1\",\"object\":\"event\",\"type\":\"customer.created\",\"data\":{\"object\":{\"id\":\"cus_1\",\"object\":\"customer\"}}}";
    private final ProcessedStripeEventRepository events = mock(ProcessedStripeEventRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
    private final RefundRepository refunds = mock(RefundRepository.class);
    private final OrderServiceClient orders = mock(OrderServiceClient.class);
    private final UserServiceClient users = mock(UserServiceClient.class);
    private final NotificationServiceClient notifications = mock(NotificationServiceClient.class);
    private final PaymentGatewayConfig config = new PaymentGatewayConfig();
    private StripeWebhookService stripe;
    private MockMvc mvc;

    @BeforeEach void setup() {
        config.getGateways().getStripe().setWebhookSecret(SECRET);
        stripe = spy(new StripeWebhookService(config, new ObjectMapper()));
        var service = new WebhookServiceImpl(mock(PaymentRetryAttempts.class), mock(StripeRetryVerifier.class), mock(PaymentOutbox.class), mock(PaymentNotificationOutbox.class),
                mock(PaymentGatewayFactory.class), stripe, payments, methods,
                refunds, orders, events, users, notifications, mock(PaymentOrderGuard.class));
        mvc = MockMvcBuilders.standaloneSetup(new WebhookController(service, config, mock(VnpayPaymentGateway.class),
                payments, events, orders, notifications, users, mock(VnpayIpnService.class)))
                .setControllerAdvice(new PaymentExceptionHandler()).build();
    }

    private String sign(String body) throws Exception {
        long time = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "t=" + time + ",v1=" + HexFormat.of().formatHex(mac.doFinal((time + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    @Test void rejectsMissingAndInvalidSignaturesWithoutWrites() throws Exception {
        mvc.perform(post("/webhooks/stripe").content(BODY)).andExpect(status().isBadRequest());
        for (String signature : new String[]{"invalid", "test_signature", "t", "t=abc,v1=x",
                "t=999999999999999999999999,v1=x", "t=" + Instant.now().getEpochSecond() + ",v1"})
            mvc.perform(post("/webhooks/stripe").content(BODY).header("Stripe-Signature", signature)).andExpect(status().isBadRequest());
        verifyNoInteractions(events, payments, methods, refunds, orders, users, notifications);
    }

    @Test void rejectsSignedMalformedJsonWithoutWrites() throws Exception {
        String body = "{broken";
        mvc.perform(post("/webhooks/stripe").content(body).header("Stripe-Signature", sign(body))).andExpect(status().isBadRequest());
        verifyNoInteractions(events, payments, methods, refunds, orders, users, notifications);
    }

    @Test void acceptsSignedEvent() throws Exception {
        mvc.perform(post("/webhooks/stripe").content(BODY).header("Stripe-Signature", sign(BODY))).andExpect(status().isOk());
        verify(events).save(any());
    }

    @Test void signedNullIsRejected() throws Exception {
        String body = "null";
        mvc.perform(post("/webhooks/stripe").content(body).header("Stripe-Signature", sign(body)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(events, payments, methods, refunds, orders, users, notifications);
    }

    @Test void signedIncompatibleDataIsInvalidPayload() throws Exception {
        for (String body : new String[]{"{\"type\":{},\"data\":{}}", "{\"type\":\"customer.created\",\"data\":[]}",
                "{\"type\":\"customer.created\",\"data\":{\"object\":[]}}"}) {
            mvc.perform(post("/webhooks/stripe").content(body).header("Stripe-Signature", sign(body)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(events, payments, methods, refunds, orders, users, notifications);
    }

    @Test void technicalFailuresRemainRetryable() throws Exception {
        when(events.existsById(anyString())).thenThrow(new IllegalStateException("repository unavailable"));
        mvc.perform(post("/webhooks/stripe").content(BODY).header("Stripe-Signature", sign(BODY))).andExpect(status().isInternalServerError());
        doThrow(new IllegalStateException("verifier unavailable")).when(stripe).verifyWebhookSignature(anyString(), anyString(), any());
        mvc.perform(post("/webhooks/stripe").content(BODY).header("Stripe-Signature", sign(BODY))).andExpect(status().isInternalServerError());
    }

    @Test void missingSecretIsRetryable() throws Exception {
        config.getGateways().getStripe().setWebhookSecret(null);
        mvc.perform(post("/webhooks/stripe").content(BODY).header("Stripe-Signature", sign(BODY))).andExpect(status().isInternalServerError());
        verifyNoInteractions(events);
    }
}
