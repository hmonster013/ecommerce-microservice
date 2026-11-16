package org.de013.paymentservice.gateway.stripe;

import com.stripe.Stripe;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StripeClosureVerifierTest {
    @Test void nativeSdkRetrievesCancelsWithStableKeyAndRequiresCompleteProof() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var mode = new AtomicReference<>("valid"); var count = new AtomicInteger(); var keys = new java.util.ArrayList<String>();
        server.createContext("/v1/payment_intents/pi_test", exchange -> {
            boolean cancel = "POST".equals(exchange.getRequestMethod());
            if (cancel) keys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            int n = count.getAndIncrement();
            String status = cancel || n % 3 == 2 ? "canceled" : "requires_confirmation";
            String amount = mode.get().equals("missingAmount") ? "" : ",\"amount\":2500";
            String charge = mode.get().equals("missingCharge") ? "" : ",\"latest_charge\":null";
            String received = mode.get().equals("missingReceived") ? "" : ",\"amount_received\":0";
            String id = mode.get().equals("mismatch") ? "pi_other" : "pi_test";
            String body = "{\"id\":\"" + id + "\",\"object\":\"payment_intent\",\"status\":\"" + status
                    + "\",\"currency\":\"usd\",\"livemode\":false" + amount + charge + received + "}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        String oldBase = Stripe.getApiBase(), oldKey = Stripe.apiKey;
        server.start();
        try {
            Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); Stripe.apiKey = "sk_test_fixture";
            var verifier = new StripeClosureVerifier(); UUID operation = UUID.randomUUID();
            assertTrue(verifier.verify("pi_test", false, 2500L, "USD", operation, 7L).safe());
            count.set(0);
            assertTrue(verifier.verify("pi_test", false, 2500L, "USD", operation, 7L).safe());
            assertEquals(2, keys.size()); assertEquals(keys.get(0), keys.get(1)); assertNotNull(keys.get(0));
            for (String scenario : new String[]{"mismatch", "missingAmount", "missingCharge", "missingReceived"}) {
                mode.set(scenario); count.set(0);
                assertFalse(verifier.verify("pi_test", false, 2500L, "USD", operation, 7L).safe(), scenario);
            }
            assertFalse(verifier.verify(null, false, 2500L, "USD", operation, 7L).safe());
        } finally { Stripe.overrideApiBase(oldBase); Stripe.apiKey = oldKey; server.stop(0); }
    }
}
