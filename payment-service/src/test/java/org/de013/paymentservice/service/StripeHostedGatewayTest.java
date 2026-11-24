package org.de013.paymentservice.service;

import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import org.de013.paymentservice.config.PaymentGatewayConfig;
import org.de013.paymentservice.entity.HostedCheckout;
import org.de013.paymentservice.gateway.stripe.StripeHostedGateway;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StripeHostedGatewayTest {
    @Test void sendsCardUsdServerAmountWithStableGenerationIdempotency() throws Exception {
        var config = new PaymentGatewayConfig(); config.getGateways().getStripe().setApiKey("sk_test_fixture_not_real");
        var gateway = new StripeHostedGateway(config);
        var checkout = new HostedCheckout(); checkout.setOrderId(7L); checkout.setGeneration(2); checkout.setAmount(new BigDecimal("25.00"));
        checkout.setSuccessUrl("https://fixture.test/checkout/result?orderId=7"); checkout.setCancelUrl(checkout.getSuccessUrl());
        var response = new Session(); response.setId("cs_fixture");
        try (var sessions = mockStatic(Session.class)) {
            sessions.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenAnswer(invocation -> {
                        SessionCreateParams params = invocation.getArgument(0); RequestOptions options = invocation.getArgument(1);
                        assertEquals("hosted-order-7-2", options.getIdempotencyKey());
                        assertEquals("7", params.getClientReferenceId()); assertEquals(SessionCreateParams.Mode.PAYMENT, params.getMode());
                        assertEquals(2500L, params.getLineItems().get(0).getPriceData().getUnitAmount());
                        assertEquals("usd", params.getLineItems().get(0).getPriceData().getCurrency());
                        assertEquals(java.util.Map.of("enabled", false), params.getExtraParams().get("adaptive_pricing"));
                        assertEquals(SessionCreateParams.PaymentMethodType.CARD, params.getPaymentMethodTypes().get(0));
                        return response;
                    });
            gateway.create(checkout); gateway.create(checkout);
            sessions.verify(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)), times(2));
        }
    }

    @Test void missingProviderCredentialsDoNotMockSuccessfulPayments() {
        var gateway = new StripeHostedGateway(new PaymentGatewayConfig());
        assertThrows(ResponseStatusException.class, () -> gateway.retrieve("cs_fixture"));
    }
}
