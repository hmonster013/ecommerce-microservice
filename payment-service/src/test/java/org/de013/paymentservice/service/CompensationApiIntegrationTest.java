package org.de013.paymentservice.service;

import com.stripe.Stripe;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.de013.paymentservice.security.CheckoutClosureSecurityTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = {"spring.cloud.bus.enabled=false", "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect", "payment.compensation.enabled=true", "payment.compensation.stripe.expected-account-id=acct_test"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompensationApiIntegrationTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", DB::getJdbcUrl); p.add("spring.datasource.username", DB::getUsername);
        p.add("spring.datasource.password", DB::getPassword); p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        p.add("payment.internal.issuer-uri", () -> CheckoutClosureSecurityTest.issuer);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CompensationProofService service;
    @Autowired CompensationProofStore store;
    @Autowired PaymentOrderGuard guard;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    @Autowired WebhookService webhook;

    private String token(String issuer, String aud, String azp, String role) throws Exception {
        return CheckoutClosureSecurityTest.tokenWithRole(issuer, aud, azp, role, Instant.now().plusSeconds(300), false);
    }
    private String token(String role) throws Exception { return token(CheckoutClosureSecurityTest.issuer, "payment-service", "order-service", role); }
    private String path(long order) { return "/internal/reconciliation/orders/" + order + "/compensation-proofs"; }
    private long payment(long order, String intent, boolean provenance) {
        return jdbc.queryForObject("insert into payments(payment_number,order_id,user_id,amount,currency,status,method,gateway_name,stripe_payment_intent_id,stripe_intent_livemode,stripe_intent_created_by_service,blocking) values (?,?, 'owner',25,'USD','SUCCEEDED','CARD','STRIPE',?,false,?,false) returning id", Long.class, "api-" + order + "-" + intent, order, intent, provenance);
    }
    private String body(long order, long payment, String intent, UUID op) {
        return "{\"operationId\":\"" + op + "\",\"findingRevision\":1,\"expectedPaymentId\":" + payment + ",\"expectedPaymentNumber\":\"api-" + order + "-" + intent + "\",\"expectedAmount\":25.00,\"expectedCurrency\":\"USD\",\"expectedCloseOperationId\":null}";
    }
    private void signedWebhook(String event) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(webhook.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "t=" + timestamp + ",v1=" + java.util.HexFormat.of().formatHex(mac.doFinal((timestamp + "." + event).getBytes(StandardCharsets.UTF_8)));
        mvc.perform(post("/webhooks/stripe").contentType("application/json").header("Stripe-Signature", signature).content(event)).andExpect(status().isOk());
    }

    @Test void realJwtPostAndGetUseAllPersistedAttemptsAndRetainHold() throws Exception {
        var violation = new AtomicBoolean(); var sawOther = new AtomicBoolean();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/", x -> {
            if (!"GET".equals(x.getRequestMethod()) || x.getRequestHeaders().getFirst("Stripe-Account") != null) violation.set(true);
            String path = x.getRequestURI().getPath(), q = x.getRequestURI().getRawQuery();
            if (path.endsWith("pi_2")) sawOther.set(true);
            String response = switch (path) {
                case "/v1/account" -> "{\"id\":\"acct_test\",\"object\":\"account\"}";
                case "/v1/payment_intents/pi_1" -> "{\"id\":\"pi_1\",\"object\":\"payment_intent\",\"livemode\":false,\"amount\":2500,\"amount_received\":2500,\"currency\":\"usd\",\"status\":\"succeeded\"}";
                case "/v1/payment_intents/pi_3" -> "{\"id\":\"pi_3\",\"object\":\"payment_intent\",\"livemode\":false,\"amount\":2500,\"amount_received\":2500,\"currency\":\"usd\",\"status\":\"succeeded\"}";
                case "/v1/payment_intents/pi_2" -> "{\"id\":\"pi_2\",\"object\":\"payment_intent\",\"livemode\":false,\"amount\":2500,\"amount_received\":0,\"currency\":\"usd\",\"status\":\"canceled\"}";
                case "/v1/charges" -> q != null && q.contains("pi_2") ? "{\"object\":\"list\",\"has_more\":false,\"data\":[]}" : "{\"object\":\"list\",\"has_more\":false,\"data\":[{\"id\":\"ch_1\",\"object\":\"charge\",\"payment_intent\":\"pi_1\",\"livemode\":false,\"amount\":2500,\"amount_captured\":2500,\"amount_refunded\":2500,\"currency\":\"usd\",\"captured\":true,\"paid\":true,\"status\":\"succeeded\"}]}";
                case "/v1/refunds" -> "{\"object\":\"list\",\"has_more\":false,\"data\":[{\"id\":\"re_1\",\"object\":\"refund\",\"charge\":\"ch_1\",\"payment_intent\":\"pi_1\",\"amount\":2500,\"currency\":\"usd\",\"status\":\"succeeded\"}]}";
                default -> "{}";
            };
            if (q != null && q.contains("pi_3")) response = response.replace("pi_1", "pi_3").replace("ch_1", "ch_3");
            if (q != null && q.contains("ch_3")) response = response.replace("pi_1", "pi_3").replace("ch_1", "ch_3");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8); x.getResponseHeaders().set("Content-Type", "application/json"); x.sendResponseHeaders(200, bytes.length); x.getResponseBody().write(bytes); x.close();
        });
        String oldBase = Stripe.getApiBase(), oldKey = Stripe.apiKey; server.start();
        try {
            Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort()); Stripe.apiKey = "sk_test_fixture";
            long order = 30601, p = payment(order, "pi_1", true); payment(order, "pi_2", true);
            jdbc.update("insert into payment_order_guard(order_id,reconciliation_hold) values (?,true)", order);
            UUID op = UUID.randomUUID(); String request = body(order, p, "pi_1", op);
            String resolve = token("payment-reconciliation-resolve"), read = token("payment-reconciliation-read");
            var result = mvc.perform(post(path(order)).header("Authorization", "Bearer " + resolve).contentType("application/json").content(request))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPENSATED")).andExpect(jsonPath("$.proofId").isNotEmpty()).andReturn();
            String proof = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("proofId").asText();
            mvc.perform(get(path(order) + "/" + op).header("Authorization", "Bearer " + read)).andExpect(status().isOk()).andExpect(jsonPath("$.proofId").value(proof));
            assertTrue(sawOther.get()); assertFalse(violation.get());
            assertTrue(jdbc.queryForObject("select reconciliation_hold and permanent_fence from payment_order_guard where order_id=?", Boolean.class, order));

            long history = jdbc.queryForObject("select count(*) from compensation_history where operation_id=?", Long.class, op);
            long version = jdbc.queryForObject("select financial_version from payment_order_guard where order_id=?", Long.class, order);
            String dispute = "{\"id\":\"evt_fallback_dispute\",\"object\":\"event\",\"type\":\"charge.dispute.updated\",\"livemode\":false,\"data\":{\"object\":{\"object\":\"dispute\",\"id\":\"dp_fallback\",\"charge\":\"ch_1\",\"payment_intent\":\"pi_1\",\"status\":\"needs_response\",\"amount\":2500,\"currency\":\"usd\",\"evidence_due_by\":1}}}";
            String chargeUpdate = "{\"id\":\"evt_fallback_charge_a\",\"object\":\"event\",\"type\":\"charge.updated\",\"livemode\":false,\"data\":{\"object\":{\"object\":\"charge\",\"id\":\"ch_1\",\"payment_intent\":\"pi_1\",\"status\":\"succeeded\",\"amount\":2500,\"currency\":\"usd\",\"amount_refunded\":2499}}}";
            signedWebhook(dispute); signedWebhook(dispute);
            mvc.perform(get(path(order) + "/" + op).header("Authorization", "Bearer " + read)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("HOLD")).andExpect(jsonPath("$.reason").value("UNRESOLVED_FINANCIAL_OBSERVATION"));
            assertEquals(1, jdbc.queryForObject("select count(*) from stripe_financial_observation where event_id='evt_fallback_dispute' and order_id is null", Integer.class));
            jdbc.update("delete from stripe_financial_observation where event_id='evt_fallback_dispute'");
            long independent = 30604, independentPayment = payment(independent, "pi_independent", true);
            UUID independentOp = UUID.randomUUID();
            var independentRequest = new CompensationProofStore.Request(independentOp, 1, independentPayment, "api-30604-pi_independent", new java.math.BigDecimal("25.00"), "USD", null);
            var independentClaim = store.begin(independent, independentRequest);
            var independentEvidence = new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.Evidence("acct_test", false, Instant.now(), java.util.List.of(
                    new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.ChargeEvidence("ch_independent", "pi_independent", 2500, "succeeded", java.util.List.of(new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.RefundEvidence("re_independent", 2500, "succeeded")))));
            var independentProof = store.complete(independent, independentOp, independentClaim.token(), new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.Result(true, null, independentEvidence));
            assertEquals("COMPENSATED", independentProof.status());
            long independentHistory = jdbc.queryForObject("select count(*) from compensation_history where operation_id=?", Long.class, independentOp);
            var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            var start = new java.util.concurrent.CountDownLatch(1);
            try {
                var a = pool.submit(() -> { start.await(); signedWebhook(chargeUpdate); return null; });
                var b = pool.submit(() -> { start.await(); signedWebhook(chargeUpdate.replace("evt_fallback_charge_a", "evt_fallback_charge_b").replace("2499", "2498")); return null; });
                start.countDown(); a.get(15, java.util.concurrent.TimeUnit.SECONDS); b.get(15, java.util.concurrent.TimeUnit.SECONDS);
            } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS)); }
            signedWebhook(chargeUpdate);
            assertEquals(2, jdbc.queryForObject("select count(*) from stripe_financial_observation where event_id in ('evt_fallback_charge_a','evt_fallback_charge_b') and order_id is null", Integer.class));
            mvc.perform(get(path(independent) + "/" + independentOp).header("Authorization", "Bearer " + read)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("HOLD")).andExpect(jsonPath("$.reason").value("UNRESOLVED_FINANCIAL_OBSERVATION"))
                    .andExpect(jsonPath("$.proofId").value(independentProof.proofId().toString())).andExpect(jsonPath("$.proofVersion").value(independentProof.proofVersion()));
            assertEquals(independentHistory, jdbc.queryForObject("select count(*) from compensation_history where operation_id=?", Long.class, independentOp));
            mvc.perform(get(path(order) + "/" + op).header("Authorization", "Bearer " + read)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("HOLD")).andExpect(jsonPath("$.reason").value("UNRESOLVED_FINANCIAL_OBSERVATION"))
                    .andExpect(jsonPath("$.proofId").value(proof)).andExpect(jsonPath("$.financialVersion").value(version));
            mvc.perform(post(path(order)).header("Authorization", "Bearer " + resolve).contentType("application/json").content(request))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HOLD"));
            assertEquals(history, jdbc.queryForObject("select count(*) from compensation_history where operation_id=?", Long.class, op));
            assertEquals("SUCCEEDED", jdbc.queryForObject("select status from payments where id=?", String.class, p));
            assertEquals(0, jdbc.queryForObject("select count(*) from refunds where payment_id=?", Integer.class, p));
            jdbc.update("delete from stripe_financial_observation where event_id in ('evt_fallback_dispute','evt_fallback_charge_a','evt_fallback_charge_b')");

            long other = 30602, p2 = payment(other, "pi_3", true); payment(other, "pi_4", false);
            mvc.perform(post(path(other)).header("Authorization", "Bearer " + resolve).contentType("application/json").content(body(other, p2, "pi_3", UUID.randomUUID())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HOLD"));

            ReflectionTestUtils.setField(service, "enabled", false);
            try {
                mvc.perform(post(path(30603)).header("Authorization", "Bearer " + resolve).contentType("application/json").content(request)).andExpect(status().isServiceUnavailable());
                assertEquals(0, jdbc.queryForObject("select count(*) from payment_order_guard where order_id=30603", Integer.class));
                mvc.perform(get(path(order) + "/" + op).header("Authorization", "Bearer " + read)).andExpect(status().isOk()).andExpect(jsonPath("$.proofId").value(proof));
            } finally { ReflectionTestUtils.setField(service, "enabled", true); }
        } finally { Stripe.overrideApiBase(oldBase); Stripe.apiKey = oldKey; server.stop(0); }
    }

    @Test void actualJwtFilterRejectsWrongClaimsRolesAndUnsignedHeaders() throws Exception {
        long order = 30610; String request = body(order, 1, "pi_1", UUID.randomUUID());
        mvc.perform(post(path(order)).header("X-User-Id", "order-service").contentType("application/json").content(request)).andExpect(status().isUnauthorized());
        mvc.perform(post(path(order)).header("Authorization", "Bearer " + token("payment-reconciliation-read")).contentType("application/json").content(request)).andExpect(status().isForbidden());
        for (String bad : java.util.List.of(token("https://wrong.test", "payment-service", "order-service", "payment-reconciliation-resolve"),
                token(CheckoutClosureSecurityTest.issuer, "wrong", "order-service", "payment-reconciliation-resolve"),
                token(CheckoutClosureSecurityTest.issuer, "payment-service", "wrong", "payment-reconciliation-resolve")))
            mvc.perform(post(path(order)).header("Authorization", "Bearer " + bad).contentType("application/json").content(request)).andExpect(status().isUnauthorized());
        assertEquals(0, jdbc.queryForObject("select count(*) from compensation_operation where order_id=?", Integer.class, order));
    }

    @Test void signedUnknownDisputeAfterProofIsVisibleAcrossStoreRecreation() throws Exception {
        long order = 30620, p = payment(order, "pi_unknown_test", true);
        UUID operation = UUID.randomUUID();
        var request = new CompensationProofStore.Request(operation, 1, p, "api-30620-pi_unknown_test", new java.math.BigDecimal("25.00"), "USD", null);
        var claim = store.begin(order, request);
        var evidence = new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.Evidence("acct_test", false, Instant.now(), java.util.List.of(
                new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.ChargeEvidence("ch_proved_unknown", "pi_unknown_test", 2500, "succeeded", java.util.List.of(
                        new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.RefundEvidence("re_unknown_test", 2500, "succeeded")))));
        var proof = store.complete(order, operation, claim.token(), new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier.Result(true, null, evidence));
        assertEquals("COMPENSATED", proof.status());
        String event = "{\"id\":\"evt_unknown_api_30620\",\"object\":\"event\",\"type\":\"charge.dispute.created\",\"livemode\":false,\"data\":{\"object\":{\"object\":\"dispute\",\"id\":\"dp_unknown_api\",\"charge\":\"ch_proved_unknown\"}}}";
        long timestamp = Instant.now().getEpochSecond();
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(webhook.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "t=" + timestamp + ",v1=" + java.util.HexFormat.of().formatHex(mac.doFinal((timestamp + "." + event).getBytes(StandardCharsets.UTF_8)));
        long history = jdbc.queryForObject("select count(*) from compensation_history where operation_id=?", Long.class, operation);
        for (int i = 0; i < 2; i++) mvc.perform(post("/webhooks/stripe").contentType("application/json").header("Stripe-Signature", signature).content(event)).andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("select count(*) from stripe_financial_observation where event_id='evt_unknown_api_30620' and order_id is null", Integer.class));
        mvc.perform(get(path(order) + "/" + operation).header("Authorization", "Bearer " + token("payment-reconciliation-read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HOLD"))
                .andExpect(jsonPath("$.reason").value("UNRESOLVED_FINANCIAL_OBSERVATION"))
                .andExpect(jsonPath("$.proofId").value(proof.proofId().toString()))
                .andExpect(jsonPath("$.financialVersion").value(proof.financialVersion()))
                .andExpect(jsonPath("$.proofVersion").value(proof.proofVersion()));
        var recreated = new CompensationProofStore(jdbc, guard, mapper);
        assertEquals("HOLD", recreated.view(order, operation).status());
        mvc.perform(post(path(order)).header("Authorization", "Bearer " + token("payment-reconciliation-resolve")).contentType("application/json").content(body(order, p, "pi_unknown_test", operation)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HOLD"));
        assertEquals(history, jdbc.queryForObject("select count(*) from compensation_history where operation_id=?", Long.class, operation));
        assertEquals(proof.evidence(), recreated.view(order, operation).evidence());
        jdbc.update("delete from stripe_financial_observation where event_id='evt_unknown_api_30620'");
    }
}
