package org.de013.paymentservice.gateway.stripe;

import com.stripe.Stripe;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class StripeCompensationVerifierTest {
    private static final String INTENT = "{\"id\":\"pi_1\",\"object\":\"payment_intent\",\"livemode\":false,\"amount\":2500,\"amount_received\":2500,\"currency\":\"usd\",\"status\":\"succeeded\"}";
    private static final String CHARGE = "{\"id\":\"ch_1\",\"object\":\"charge\",\"payment_intent\":\"pi_1\",\"livemode\":false,\"amount\":2500,\"amount_captured\":2500,\"amount_refunded\":2500,\"currency\":\"usd\",\"captured\":true,\"paid\":true,\"status\":\"succeeded\"}";
    private static final String REFUND = "{\"id\":\"re_1\",\"object\":\"refund\",\"charge\":\"ch_1\",\"payment_intent\":\"pi_1\",\"amount\":2500,\"currency\":\"usd\",\"status\":\"succeeded\"}";
    private static final StripeCompensationVerifier.Attempt ATTEMPT = new StripeCompensationVerifier.Attempt(1, "P-1", "pi_1", false, true, new BigDecimal("25.00"), "USD", false, "STRIPE");

    @Test void identityBindingAndAttemptCompleteness() throws Exception {
        try (var fixture = new Fixture()) {
            var first = fixture.verify(List.of(ATTEMPT)); assertTrue(first.safe(), first.reason());
            fixture.intent.set(INTENT.replace("false", "true")); assertFalse(fixture.verify(List.of(ATTEMPT)).safe()); fixture.intent.set(INTENT);
            fixture.charge.set(CHARGE.replace("pi_1", "pi_other")); assertFalse(fixture.verify(List.of(ATTEMPT)).safe()); fixture.charge.set(CHARGE);
            fixture.refund.set(REFUND.replace("ch_1", "ch_other")); assertFalse(fixture.verify(List.of(ATTEMPT)).safe());
            fixture.refund.set(REFUND.replace("pi_1", "pi_other")); assertFalse(fixture.verify(List.of(ATTEMPT)).safe()); fixture.refund.set(REFUND);
            var other = new StripeCompensationVerifier.Attempt(2, "P-2", "pi_2", false, true, new BigDecimal("25.00"), "USD", false, "STRIPE");
            assertTrue(fixture.verify(List.of(ATTEMPT, other)).safe());
            fixture.otherIntent.set(fixture.otherIntent.get().replace("canceled", "processing")); assertFalse(fixture.verify(List.of(ATTEMPT, other)).safe());
            fixture.charge.set(CHARGE + "," + CHARGE.replace("ch_1", "ch_2")); assertFalse(fixture.verify(List.of(ATTEMPT)).safe());
            var hosted = new StripeCompensationVerifier.Attempt(1, "P-1", "pi_1", false, true, new BigDecimal("25.00"), "USD", true, "STRIPE");
            assertEquals("HOSTED_PROOF_UNSUPPORTED", fixture.verify(List.of(hosted)).reason());
            assertFalse(fixture.violation.get());
        }
    }

    @Test void unsupportedProvenanceProviderAndForeignAccountFailClosed() throws Exception {
        try (var fixture = new Fixture()) {
            var v = new StripeCompensationVerifier();
            for (var a : List.of(
                    new StripeCompensationVerifier.Attempt(1, "P-1", "pi_1", false, true, new BigDecimal("25.00"), "USD", true, "STRIPE"),
                    new StripeCompensationVerifier.Attempt(1, "P-1", "pi_1", false, true, new BigDecimal("25.00"), "USD", false, "VNPAY"),
                    new StripeCompensationVerifier.Attempt(1, "P-1", "pi_1", false, false, new BigDecimal("25.00"), "USD", false, "STRIPE"),
                    new StripeCompensationVerifier.Attempt(1, "P-1", "pi_1", null, true, new BigDecimal("25.00"), "USD", false, "STRIPE")))
                assertFalse(v.verify("acct_test", List.of(a)).safe());
            assertEquals("ACCOUNT_MISMATCH", v.verify("acct_foreign", List.of(ATTEMPT)).reason());
            assertFalse(fixture.violation.get());
        }
    }

    @Test void pagesMustBeCompleteAndNonrepeating() throws Exception {
        try (var fixture = new Fixture()) {
            String failed = CHARGE.replace("ch_1", "ch_failed").replace("2500,\"amount_refunded\":2500", "0,\"amount_refunded\":0")
                    .replace("\"captured\":true", "\"captured\":false").replace("\"paid\":true", "\"paid\":false").replace("succeeded", "failed");
            fixture.charge.set(failed); fixture.nextCharge.set(CHARGE); fixture.chargeMore.set(true);
            fixture.refund.set(REFUND.replace("2500", "1200")); fixture.nextRefund.set(REFUND.replace("re_1", "re_2").replace("2500", "1300")); fixture.refundMore.set(true);
            var paged = fixture.verify(List.of(ATTEMPT)); assertTrue(paged.safe(), paged.reason());
            fixture.nextCharge.set(failed); assertFalse(fixture.verify(List.of(ATTEMPT)).safe()); fixture.nextCharge.set(CHARGE);
            fixture.nextRefund.set(fixture.refund.get()); assertFalse(fixture.verify(List.of(ATTEMPT)).safe());
            fixture.nextRefund.set(""); assertFalse(fixture.verify(List.of(ATTEMPT)).safe());
            fixture.nextRefund.set(REFUND.replace("re_1", "re_2").replace("2500", "1300"));
            fixture.repeatRefund.set(true); assertFalse(fixture.verify(List.of(ATTEMPT)).safe()); fixture.repeatRefund.set(false);
            fixture.omitHasMore.set(true); assertFalse(fixture.verify(List.of(ATTEMPT)).safe());
            assertFalse(fixture.violation.get());
        }
    }

    @Test void slowProviderReturnsHoldWithoutMutation() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.slow.set(true);
            long start = System.nanoTime();
            assertFalse(fixture.verify(List.of(ATTEMPT)).safe());
            assertTrue((System.nanoTime() - start) < 8_000_000_000L);
            assertFalse(fixture.violation.get());
        }
    }

    public static final class Fixture implements AutoCloseable {
        final AtomicReference<String> intent = new AtomicReference<>(INTENT), charge = new AtomicReference<>(CHARGE), refund = new AtomicReference<>(REFUND);
        final AtomicReference<String> nextCharge = new AtomicReference<>(""), nextRefund = new AtomicReference<>("");
        final AtomicReference<String> otherIntent = new AtomicReference<>("{\"id\":\"pi_2\",\"object\":\"payment_intent\",\"livemode\":false,\"amount\":2500,\"amount_received\":0,\"currency\":\"usd\",\"status\":\"canceled\"}");
        final java.util.concurrent.atomic.AtomicBoolean chargeMore = new java.util.concurrent.atomic.AtomicBoolean(), refundMore = new java.util.concurrent.atomic.AtomicBoolean(), repeatRefund = new java.util.concurrent.atomic.AtomicBoolean(), omitHasMore = new java.util.concurrent.atomic.AtomicBoolean(), slow = new java.util.concurrent.atomic.AtomicBoolean(), violation = new java.util.concurrent.atomic.AtomicBoolean();
        public final java.util.concurrent.atomic.AtomicInteger connectedRequests = new java.util.concurrent.atomic.AtomicInteger();
        final HttpServer server;
        final String base = Stripe.getApiBase(), key = Stripe.apiKey;
        public Fixture() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/", x -> {
                if (!"GET".equals(x.getRequestMethod()) || x.getRequestHeaders().getFirst("Stripe-Account") != null) violation.set(true);
                if (slow.get()) try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                String path = x.getRequestURI().getPath(), query = x.getRequestURI().getRawQuery();
                if ("/v1/payment_intents/pi_connected".equals(path)) {
                    connectedRequests.incrementAndGet();
                    byte[] bytes = "{\"error\":{\"type\":\"invalid_request_error\",\"code\":\"resource_missing\",\"message\":\"No such payment_intent\"}}".getBytes(StandardCharsets.UTF_8);
                    x.getResponseHeaders().set("Content-Type", "application/json");
                    try { x.sendResponseHeaders(404, bytes.length); x.getResponseBody().write(bytes); } finally { x.close(); }
                    return;
                }
                boolean next = query != null && query.contains("starting_after=");
                String body = switch (path) {
                    case "/v1/account" -> "{\"id\":\"acct_test\",\"object\":\"account\"}";
                    case "/v1/payment_intents/pi_1" -> intent.get();
                    case "/v1/payment_intents/pi_2" -> otherIntent.get();
                    case "/v1/charges" -> query != null && query.contains("pi_2") ? list("", false, false) : list(next ? nextCharge.get() : charge.get(), !next && chargeMore.get(), omitHasMore.get());
                    case "/v1/refunds" -> query != null && query.contains("ch_failed") ? list("", false, false) : list(next ? nextRefund.get() : refund.get(), repeatRefund.get() || (!next && refundMore.get()), omitHasMore.get());
                    default -> "{}";
                };
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8); x.getResponseHeaders().set("Content-Type", "application/json");
                try { x.sendResponseHeaders(200, bytes.length); x.getResponseBody().write(bytes); } finally { x.close(); }
            });
            server.start(); Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); Stripe.apiKey = "sk_test_fixture";
        }
        public StripeCompensationVerifier.Result verify(List<StripeCompensationVerifier.Attempt> a) { return new StripeCompensationVerifier().verify("acct_test", a); }
        public boolean hasViolation() { return violation.get(); }
        static String list(String data, boolean more, boolean omit) { return "{\"object\":\"list\"," + (omit ? "" : "\"has_more\":" + more + ",") + "\"data\":[" + data + "]}"; }
        @Override public void close() { Stripe.overrideApiBase(base); Stripe.apiKey = key; server.stop(0); }
    }
    @Test void providerReadsAreGetOnlyAndRefundMustBeComplete() throws Exception {
        var charge = new AtomicReference<>("{\"id\":\"ch_1\",\"object\":\"charge\",\"payment_intent\":\"pi_1\",\"livemode\":false,\"amount\":2500,\"amount_captured\":2500,\"amount_refunded\":2500,\"currency\":\"usd\",\"captured\":true,\"paid\":true,\"status\":\"succeeded\"}");
        var refund = new AtomicReference<>("{\"id\":\"re_1\",\"object\":\"refund\",\"charge\":\"ch_1\",\"payment_intent\":\"pi_1\",\"amount\":2500,\"currency\":\"usd\",\"status\":\"succeeded\"}");
        var violation = new java.util.concurrent.atomic.AtomicBoolean();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod()) || exchange.getRequestHeaders().getFirst("Stripe-Account") != null) violation.set(true);
            String path = exchange.getRequestURI().getPath();
            String body = switch (path) {
                case "/v1/account" -> "{\"id\":\"acct_test\",\"object\":\"account\"}";
                case "/v1/payment_intents/pi_1" -> "{\"id\":\"pi_1\",\"object\":\"payment_intent\",\"livemode\":false,\"amount\":2500,\"amount_received\":2500,\"currency\":\"usd\",\"status\":\"succeeded\"}";
                case "/v1/charges" -> "{\"object\":\"list\",\"has_more\":false,\"data\":[" + charge.get() + "]}";
                case "/v1/refunds" -> "{\"object\":\"list\",\"has_more\":false,\"data\":[" + refund.get() + "]}";
                default -> "{}";
            };
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        String base = Stripe.getApiBase(), key = Stripe.apiKey;
        server.start();
        try {
            Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); Stripe.apiKey = "sk_test_fixture";
            var attempt = new StripeCompensationVerifier.Attempt(1, "P-1", "pi_1", false, true, new BigDecimal("25.00"), "USD", false, "STRIPE");
            var verifier = new StripeCompensationVerifier();
            var full = verifier.verify("acct_test", List.of(attempt)); assertTrue(full.safe(), full.reason());
            assertFalse(verifier.verify("acct_other", List.of(attempt)).safe());
            refund.set(refund.get().replace("2500", "2400"));
            assertFalse(verifier.verify("acct_test", List.of(attempt)).safe());
            refund.set(refund.get().replace("2400", "2500").replace("succeeded", "pending"));
            assertFalse(verifier.verify("acct_test", List.of(attempt)).safe());
            assertFalse(violation.get());
        } finally { Stripe.overrideApiBase(base); Stripe.apiKey = key; server.stop(0); }
    }
}
