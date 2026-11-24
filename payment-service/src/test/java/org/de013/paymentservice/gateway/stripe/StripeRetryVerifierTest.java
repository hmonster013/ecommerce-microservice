package org.de013.paymentservice.gateway.stripe;

import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.enums.Currency;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StripeRetryVerifierTest {
    @Test void rawZeroDecimalAmountsMustMatchExactly() {
        var gateway = org.mockito.Mockito.mock(StripePaymentGateway.class);
        org.mockito.Mockito.when(gateway.getSupportedCurrencies()).thenCallRealMethod();
        var verifier = new StripeRetryVerifier(gateway);
        for (var currency : new Currency[]{Currency.JPY, Currency.VND}) {
            var payment = Payment.builder().gatewayName("STRIPE").stripeIntentCreatedByService(true).stripeIntentLivemode(false)
                    .stripePaymentIntentId("pi_zero").currency(currency).amount(new BigDecimal("25.00")).build();
            var intent = new PaymentIntent(); intent.setId("pi_zero"); intent.setLivemode(false);
            intent.setCurrency(currency.name().toLowerCase(java.util.Locale.ROOT)); intent.setAmount(25L);
            assertTrue(verifier.matches(payment, intent));
            intent.setAmount(2500L);
            assertFalse(verifier.matches(payment, intent));
        }
    }

    @Test void onlyMatchingRetryableIntentIsRead() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var body = new AtomicReference<String>(); var posts = new AtomicInteger();
        server.createContext("/v1/payment_intents/pi_retry", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) posts.incrementAndGet();
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        String base = Stripe.getApiBase(), key = Stripe.apiKey; server.start();
        try {
            Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); Stripe.apiKey = "sk_test_fixture";
            var payment = Payment.builder().gatewayName("STRIPE").stripeIntentCreatedByService(true).stripeIntentLivemode(false)
                    .stripePaymentIntentId("pi_retry").currency(Currency.USD).amount(new BigDecimal("25.00")).build();
            var gateway = org.mockito.Mockito.mock(StripePaymentGateway.class);
            org.mockito.Mockito.when(gateway.getSupportedCurrencies()).thenCallRealMethod();
            var verifier = new StripeRetryVerifier(gateway);
            String valid = "{\"id\":\"pi_retry\",\"object\":\"payment_intent\",\"livemode\":false,\"currency\":\"usd\",\"amount\":2500,\"status\":\"requires_payment_method\"}";
            body.set(valid); assertTrue(verifier.eligible(payment));
            for (String changed : new String[]{valid.replace("pi_retry", "pi_other"), valid.replace("false", "true"), valid.replace("2500", "2501"), valid.replace("usd", "eur"), valid.replace("requires_payment_method", "processing")}) {
                body.set(changed); assertFalse(verifier.eligible(payment));
            }
            payment.setCurrency(Currency.SGD); body.set(valid.replace("usd", "sgd")); assertTrue(verifier.eligible(payment));
            payment.setCurrency(Currency.VND); body.set(valid.replace("usd", "vnd").replace("2500", "25")); assertTrue(verifier.eligible(payment));
            payment.setStripeIntentCreatedByService(false); assertFalse(verifier.eligible(payment));
            assertEquals(0, posts.get());
        } finally { Stripe.overrideApiBase(base); Stripe.apiKey = key; server.stop(0); }
    }
}
