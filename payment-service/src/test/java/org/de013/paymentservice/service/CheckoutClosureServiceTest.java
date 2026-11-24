package org.de013.paymentservice.service;

import java.math.BigDecimal;
import com.stripe.Stripe;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.de013.paymentservice.repository.PaymentRepository;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = {"spring.cloud.bus.enabled=false", "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"})
@ActiveProfiles("test")
class CheckoutClosureServiceTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", DB::getJdbcUrl);
        p.add("spring.datasource.username", DB::getUsername);
        p.add("spring.datasource.password", DB::getPassword);
        p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
    @Autowired CheckoutClosureService closures;
    @Autowired HostedCheckoutStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired PaymentRepository payments;
    @Autowired PaymentOrderGuard guard;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper json;
    @Autowired CompensationProofStore proofStore;

    private CompensationProofStore.Request compensationRequest(long order, long payment, String intent, UUID close) {
        return new CompensationProofStore.Request(UUID.randomUUID(), 1, payment, "fixture-" + intent, new BigDecimal("25.00"), "USD", close);
    }
    private org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.Result compensationEvidence(String intent) {
        var evidence = new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.Evidence("acct_test", false, java.time.Instant.now(), java.util.List.of(
                new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.ChargeEvidence("ch_" + intent, intent, 2500, "succeeded", java.util.List.of(
                        new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.RefundEvidence("re_" + intent, 2500, "succeeded")))));
        return new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.Result(true, null, evidence);
    }

    @Test void compensationClaimDetectsClosureBindingAndPreexistingHoldAndNoAttempts() {
        for (int i = 0; i < 3; i++) {
            long order = 988100 + i; String intent = "pi_close_c1_" + i;
            long payment = i == 2 ? 999999 : trusted(order, intent);
            if (i == 1) jdbc.update("update payments set stripe_intent_created_by_service=false where id=?", payment);
            if (i == 0) new TransactionTemplate(transactions).executeWithoutResult(s -> guard.hold(order));
            var request = compensationRequest(order, payment, intent, null);
            var claim = proofStore.begin(order, request);
            var result = closures.close(order, UUID.randomUUID());
            if (i == 0) assertEquals("RECONCILIATION_HOLD", result.reason());
            if (i == 2) assertEquals("SAFE", result.status());
            assertTrue(jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, order) > claim.view().financialVersion());
            var completed = proofStore.complete(order, request.operationId(), claim.token(), compensationEvidence(intent));
            assertEquals("HOLD", completed.status()); assertEquals("FINANCIAL_FACTS_CHANGED", completed.reason());
        }
    }

    @Test void compensationClaimDetectsPackageClosureHoldCompletion() {
        long order = 988110; String intent = "pi_close_helper"; long payment = trusted(order, intent); UUID close = UUID.randomUUID();
        var processing = closures.beginProcessing(order, close); assertNotNull(processing.claim());
        var request = compensationRequest(order, payment, intent, close); var claim = proofStore.begin(order, request);
        assertTrue(closures.completeProcessing(order, close, processing.claim(), "HOLD", "FIXTURE"));
        assertTrue(jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, order) > claim.view().financialVersion());
        var completed = proofStore.complete(order, request.operationId(), claim.token(), compensationEvidence(intent));
        assertEquals("HOLD", completed.status()); assertEquals("FINANCIAL_FACTS_CHANGED", completed.reason());
    }

    @Test void compensationClaimDetectsInFlightRealClosureSafeAndUnsafeCompletion() throws Exception {
        for (boolean safe : new boolean[]{true, false}) {
            long order = safe ? 988120 : 988121; String intent = safe ? "pi_close_safe_c1" : "pi_close_unsafe_c1";
            long payment = trusted(order, intent); UUID close = UUID.randomUUID();
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/payment_intents/" + intent, exchange -> {
                entered.countDown();
                try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                String body = "{\"id\":\"" + intent + "\",\"object\":\"payment_intent\",\"status\":\"" + (safe ? "canceled" : "processing") + "\",\"currency\":\"usd\",\"livemode\":false,\"amount\":2500,\"amount_received\":0,\"latest_charge\":null}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
            });
            String base = Stripe.getApiBase(), key = Stripe.apiKey; var pool = Executors.newSingleThreadExecutor(); server.start();
            try {
                Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); Stripe.apiKey = "sk_test_fixture";
                var future = pool.submit(() -> closures.close(order, close));
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var request = compensationRequest(order, payment, intent, close); var claim = proofStore.begin(order, request);
                release.countDown(); var result = future.get(5, TimeUnit.SECONDS);
                assertEquals(safe ? "SAFE" : "HOLD", result.status());
                assertTrue(jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, order) > claim.view().financialVersion());
                var completed = proofStore.complete(order, request.operationId(), claim.token(), compensationEvidence(intent));
                assertEquals("HOLD", completed.status()); assertEquals("FINANCIAL_FACTS_CHANGED", completed.reason());
            } finally {
                release.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
                Stripe.overrideApiBase(base); Stripe.apiKey = key; server.stop(0);
            }
        }
    }

    private long trusted(long order, String intent) {
        return jdbc.queryForObject("INSERT INTO payments (payment_number, order_id, user_id, amount, currency, status, method, stripe_payment_intent_id, stripe_intent_livemode, stripe_intent_created_by_service, blocking) VALUES (?, ?, 'owner', 25.00, 'USD', 'PENDING', 'CARD', ?, false, true, false) RETURNING id", Long.class, "fixture-" + intent, order, intent);
    }

    @Test void closureBindingAndContradictionAdvanceVersionOnce() {
        long order = 987902L;
        UUID operation = UUID.randomUUID();
        assertEquals("SAFE", closures.close(order, operation).status());
        long bound = jdbc.queryForObject("SELECT financial_version FROM payment_order_guard WHERE order_id=?", Long.class, order);
        assertTrue(bound > 0);
        closures.close(order, operation);
        assertEquals(bound, jdbc.queryForObject("SELECT financial_version FROM payment_order_guard WHERE order_id=?", Long.class, order));
        closures.recordContradiction(order);
        assertEquals(bound + 1, jdbc.queryForObject("SELECT financial_version FROM payment_order_guard WHERE order_id=?", Long.class, order));
        closures.recordContradiction(order);
        assertEquals(bound + 1, jdbc.queryForObject("SELECT financial_version FROM payment_order_guard WHERE order_id=?", Long.class, order));
    }

    @Test void multipleAttemptProofAndMixedPreflightAndTakeover() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var cancels = new CopyOnWriteArrayList<String>();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var blocked = new AtomicBoolean();
        var canceled = java.util.concurrent.ConcurrentHashMap.<String>newKeySet();
        server.createContext("/v1/payment_intents/", exchange -> {
            String[] parts = exchange.getRequestURI().getPath().split("/"); String id = parts[3];
            boolean post = "POST".equals(exchange.getRequestMethod());
            if (post) {
                cancels.add(id);
                if (id.equals("pi_takeover") && blocked.compareAndSet(false, true)) {
                    entered.countDown();
                    try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                canceled.add(id);
            }
            int code = id.equals("pi_401") ? 401 : id.equals("pi_404") ? 404 : 200;
            String body = code == 200 ? "{\"id\":\"" + id + "\",\"object\":\"payment_intent\",\"status\":\"" + (canceled.contains(id) ? "canceled" : "requires_confirmation") + "\",\"currency\":\"usd\",\"livemode\":false,\"amount\":2500,\"amount_received\":0,\"latest_charge\":null}" : "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"fixture\"}}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            try { exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(code, bytes.length); exchange.getResponseBody().write(bytes); } finally { exchange.close(); }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        String base = Stripe.getApiBase(), key = Stripe.apiKey; server.start();
        var executor = Executors.newSingleThreadExecutor();
        try {
            Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); Stripe.apiKey = "sk_test_fixture";
            long order = 987040L; long a = trusted(order, "pi_proof_a"), b = trusted(order, "pi_proof_b"); UUID op = UUID.randomUUID();
            assertEquals("SAFE", closures.close(order, op).status());
            String proof = jdbc.queryForObject("SELECT close_safe_proof FROM payment_order_guard WHERE order_id=?", String.class, order);
            var root = json.readTree(proof); assertEquals(op.toString(), root.path("operationId").asText()); assertEquals(2, root.path("attempts").size());
            for (int i = 0; i < 2; i++) {
                var evidence = root.path("attempts").get(i);
                assertEquals(i == 0 ? a : b, evidence.path("paymentId").asLong());
                assertEquals(i == 0 ? "pi_proof_a" : "pi_proof_b", evidence.path("paymentIntentId").asText());
                assertEquals(op.toString(), evidence.path("operationId").asText()); assertFalse(evidence.path("livemode").asBoolean());
                assertEquals(2500, evidence.path("amount").asLong()); assertEquals("usd", evidence.path("currency").asText());
                assertEquals("canceled", evidence.path("status").asText()); assertTrue(evidence.path("amountReceivedPresent").asBoolean());
                assertEquals(0, evidence.path("amountReceived").asLong()); assertTrue(evidence.path("latestChargePresent").asBoolean());
                assertTrue(evidence.has("latestCharge") && evidence.get("latestCharge").isNull()); assertFalse(evidence.path("verifiedAt").asText().isBlank());
            }
            closures.recordContradiction(order); assertEquals("HOLD", closures.close(order, op).status());
            assertEquals(proof, jdbc.queryForObject("SELECT close_safe_proof FROM payment_order_guard WHERE order_id=?", String.class, order));
            for (int i = 0; i < 4; i++) {
                long mixed = 987050L + i; trusted(mixed, "pi_mixed_" + i);
                if (i == 0) { long id = trusted(mixed, "pi_legacy"); jdbc.update("UPDATE payments SET stripe_intent_created_by_service=false WHERE id=?", id); }
                if (i == 1) { long id = trusted(mixed, "pi_hosted"); jdbc.update("INSERT INTO hosted_checkouts (order_id,user_id,payment_id,amount,success_url,cancel_url,reserved_at) VALUES (?, 'owner', ?, 25.00, 'https://fixture.test/result', 'https://fixture.test/result', current_timestamp)", mixed, id); }
                if (i == 2) { long id = trusted(mixed, "pi_vnpay"); jdbc.update("UPDATE payments SET gateway_name='VNPAY' WHERE id=?", id); }
                if (i == 3) { long id = trusted(mixed, "pi_success"); jdbc.update("UPDATE payments SET status='SUCCEEDED' WHERE id=?", id); }
                int before = cancels.size(); assertEquals("HOLD", closures.close(mixed, UUID.randomUUID()).status()); assertEquals(before, cancels.size());
            }
            for (int code : new int[]{401,404}) {
                long id = 987100L + code; trusted(id, "pi_" + code); UUID request = UUID.randomUUID();
                assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class, () -> closures.close(id, request)).getStatusCode());
                assertEquals("CLOSING", jdbc.queryForObject("SELECT close_status FROM payment_order_guard WHERE order_id=?", String.class, id));
            }
            long takeover = 987060L; trusted(takeover, "pi_takeover"); UUID takeoverOp = UUID.randomUUID();
            var first = executor.submit(() -> closures.close(takeover, takeoverOp)); assertTrue(entered.await(5, TimeUnit.SECONDS));
            jdbc.update("UPDATE payment_order_guard SET close_claim_until=clock_timestamp()-interval '1 second' WHERE order_id=?", takeover);
            assertEquals("SAFE", closures.close(takeover, takeoverOp).status()); release.countDown();
            var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> first.get(10, TimeUnit.SECONDS));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ((ResponseStatusException) failure.getCause()).getStatusCode());
            assertEquals("SAFE", closures.close(takeover, takeoverOp).status());
        } finally { release.countDown(); executor.shutdownNow(); Stripe.overrideApiBase(base); Stripe.apiKey = key; server.stop(0); ((java.util.concurrent.ExecutorService) server.getExecutor()).shutdownNow(); }
    }

    @Test void trustedStripeSdkProofIsDurableAndReplayed() throws Exception { stripeScenario(987030L, "safe"); }
    @Test void lateSuccessDuringStripeVerificationRemainsFinancialTruthAndHolds() throws Exception { stripeScenario(987031L, "late"); }
    @Test void technicalFailureRetriesSameOperationAndStableKey() throws Exception { stripeScenario(987032L, "technical"); }

    private void stripeScenario(long order, String scenario) throws Exception {
        long payment = jdbc.queryForObject("INSERT INTO payments (payment_number, order_id, user_id, amount, currency, status, method, stripe_payment_intent_id, stripe_intent_livemode, stripe_intent_created_by_service) VALUES (?, ?, 'owner', 25.00, 'USD', 'PENDING', 'CREDIT_CARD', ?, false, true) RETURNING id", Long.class, "closure-" + order, order, "pi_closure_" + order);
        UUID op = UUID.randomUUID();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var failed = new AtomicBoolean(); var canceled = new AtomicBoolean();
        var keys = new CopyOnWriteArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/payment_intents/pi_closure_" + order, exchange -> {
            boolean post = "POST".equals(exchange.getRequestMethod());
            if (post) {
                keys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                if (scenario.equals("late")) {
                    entered.countDown();
                    try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                }
                if (scenario.equals("technical") && failed.compareAndSet(false, true)) {
                    byte[] error = "{\"error\":{\"type\":\"api_error\",\"message\":\"fixture unavailable\"}}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(500, error.length);
                    exchange.getResponseBody().write(error); exchange.close(); return;
                }
                canceled.set(true);
            }
            String body = "{\"id\":\"pi_closure_" + order + "\",\"object\":\"payment_intent\",\"status\":\"" + (canceled.get() ? "canceled" : "requires_confirmation") + "\",\"currency\":\"usd\",\"livemode\":false,\"amount\":2500,\"amount_received\":0,\"latest_charge\":null}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        String base = Stripe.getApiBase(), key = Stripe.apiKey;
        var executor = Executors.newSingleThreadExecutor(); server.start();
        try {
            Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); Stripe.apiKey = "sk_test_fixture";
            if (scenario.equals("late")) {
                var closing = executor.submit(() -> closures.close(order, op));
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                    guard.lock(order);
                    jdbc.update("UPDATE payments SET status='SUCCEEDED' WHERE id=?", payment);
                    guard.recordSuccess(order);
                });
                release.countDown();
                assertEquals("HOLD", closing.get(10, TimeUnit.SECONDS).status());
                assertEquals("SUCCEEDED", jdbc.queryForObject("SELECT status FROM payments WHERE id=?", String.class, payment));
            } else {
                if (scenario.equals("technical")) {
                    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class, () -> closures.close(order, op)).getStatusCode());
                    assertEquals("CLOSING", jdbc.queryForObject("SELECT close_status FROM payment_order_guard WHERE order_id=?", String.class, order));
                }
                assertEquals("SAFE", closures.close(order, op).status());
                var proof = jdbc.queryForMap("SELECT close_safe_proof, close_safe_at FROM payment_order_guard WHERE order_id=?", order);
                assertTrue(proof.get("close_safe_proof").toString().contains("pi_closure_" + order));
                assertNotNull(proof.get("close_safe_at"));
                assertEquals("SAFE", closures.close(order, op).status());
                assertEquals(proof, jdbc.queryForMap("SELECT close_safe_proof, close_safe_at FROM payment_order_guard WHERE order_id=?", order));
                if (scenario.equals("technical")) { assertEquals(2, keys.size()); assertEquals(keys.get(0), keys.get(1)); }
            }
        } finally {
            release.countDown(); executor.shutdownNow(); Stripe.overrideApiBase(base); Stripe.apiKey = key; server.stop(0);
        }
    }

    @Test void persistsAndReplaysMonotonicClosure() {
        long empty = 987001L, attempted = 987002L;
        UUID operation = UUID.randomUUID();
        assertEquals("SAFE", closures.close(empty, operation).status());
        assertEquals("SAFE", closures.close(empty, operation).status());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> closures.close(empty, UUID.randomUUID())).getStatusCode());
        var proof = jdbc.queryForMap("SELECT close_safe_proof, close_safe_at FROM payment_order_guard WHERE order_id = ?", empty);
        assertEquals("NO_PAYMENT_ATTEMPTS", proof.get("close_safe_proof"));
        assertNotNull(proof.get("close_safe_at"));
        closures.recordContradiction(empty);
        assertEquals("HOLD", closures.close(empty, operation).status());
        assertEquals(proof, jdbc.queryForMap("SELECT close_safe_proof, close_safe_at FROM payment_order_guard WHERE order_id = ?", empty));
        store.reserve(attempted, "owner", new BigDecimal("25.00"), "https://fixture.test/result");
        UUID second = UUID.randomUUID();
        assertEquals("HOLD", closures.close(attempted, second).status());
        assertEquals("HOLD", closures.close(attempted, second).status());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> closures.close(0L, second)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> closures.close(empty, null)).getStatusCode());
    }

    @Test void fenceSerializesReservationAndRecordsLateHostedTruth() throws Exception {
        long empty = 987010L, pending = 987011L;
        UUID operation = UUID.randomUUID();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var reservation = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                guard.requirePayable(empty);
                locked.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return null;
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            var closing = executor.submit(() -> closures.close(empty, operation));
            release.countDown();
            reservation.get(10, TimeUnit.SECONDS);
            assertEquals("SAFE", closing.get(10, TimeUnit.SECONDS).status());
            assertThrows(ResponseStatusException.class,
                    () -> store.reserve(empty, "owner", new BigDecimal("25.00"), "https://fixture.test/result"));

            var checkout = store.reserve(pending, "owner", new BigDecimal("25.00"), "https://fixture.test/result");
            UUID pendingOp = UUID.randomUUID();
            assertEquals("HOLD", closures.close(pending, pendingOp).status());
            assertThrows(ResponseStatusException.class, () -> store.create(checkout));
            store.record(pending, 0, "cs_late", "PAID", "pi_late");
            assertEquals(PaymentStatus.SUCCEEDED, payments.findById(checkout.getPaymentId()).orElseThrow().getStatus());
            assertEquals("HOLD", closures.close(pending, pendingOp).status());
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM payment_outbox WHERE payment_id = ?", Integer.class, checkout.getPaymentId()));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test void closingClaimExpiresAndStaleOwnerCannotCompleteOrPromoteHold() {
        long id = 987020L; UUID op = UUID.randomUUID();
        store.reserve(id, "owner", new BigDecimal("25.00"), "https://fixture.test/result");
        var first = closures.beginProcessing(id, op);
        assertNotNull(first.claim());
        assertEquals("CLOSING", jdbc.queryForObject("select close_status from payment_order_guard where order_id=?", String.class, id));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class, () -> closures.beginProcessing(id, op)).getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class, () -> closures.close(id, op)).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> closures.beginProcessing(id, UUID.randomUUID())).getStatusCode());
        jdbc.update("update payment_order_guard set close_claim_until = clock_timestamp() - interval '1 second' where order_id=?", id);
        var second = closures.beginProcessing(id, op);
        assertNotEquals(first.claim(), second.claim());
        assertFalse(closures.completeProcessing(id, op, first.claim(), "HOLD", "STALE"));
        assertThrows(IllegalArgumentException.class, () -> closures.completeProcessing(id, op, second.claim(), "SAFE", null));
        assertTrue(closures.completeProcessing(id, op, second.claim(), "HOLD", "UNKNOWN"));
        assertEquals("HOLD", closures.beginProcessing(id, op).result().status());
        assertFalse(closures.completeProcessing(id, op, second.claim(), "HOLD", "STALE"));
        assertEquals("UNKNOWN", closures.close(id, op).reason());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("update payment_order_guard set close_status='CLOSING' where order_id=?", id));
    }
}
