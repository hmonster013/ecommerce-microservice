package org.de013.paymentservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = {"spring.cloud.bus.enabled=false", "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"})
@ActiveProfiles("test")
class CompensationProofStoreTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", DB::getJdbcUrl); p.add("spring.datasource.username", DB::getUsername);
        p.add("spring.datasource.password", DB::getPassword); p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        p.add("payment.internal.issuer-uri", () -> org.de013.paymentservice.security.CheckoutClosureSecurityTest.issuer);
    }
    @Autowired CompensationProofStore store;
    @Autowired PaymentOrderGuard guard;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper mapper;

    private long payment(long order) {
        return jdbc.queryForObject("INSERT INTO payments(payment_number,order_id,user_id,amount,currency,status,method,gateway_name,stripe_payment_intent_id,stripe_intent_livemode,stripe_intent_created_by_service,receipt_email) VALUES (?,?, 'owner',25,'USD','SUCCEEDED','CARD','STRIPE',?,false,true,'private@example.test') RETURNING id", Long.class, "proof-" + order, order, "pi_" + order);
    }
    private CompensationProofStore.Request request(long order, long payment) {
        return new CompensationProofStore.Request(UUID.randomUUID(), 1, payment, "proof-" + order, new BigDecimal("25.00"), "USD", null);
    }
    private StripeCompensationVerifier.Result proof(long order) {
        var refund = new StripeCompensationVerifier.RefundEvidence("re_" + order, 2500, "succeeded");
        var charge = new StripeCompensationVerifier.ChargeEvidence("ch_" + order, "pi_" + order, 2500, "succeeded", List.of(refund));
        return new StripeCompensationVerifier.Result(true, null, new StripeCompensationVerifier.Evidence("acct_test", false, Instant.now(), List.of(charge)));
    }

    @Test void proofIsImmutableAndInvalidationPreservesHistory() {
        long order = 29101, p = payment(order); var request = request(order, p); var claim = store.begin(order, request);
        assertNotNull(claim.token()); assertTrue(jdbc.queryForObject("select permanent_fence from payment_order_guard where order_id=?", Boolean.class, order));
        String snapshot = jdbc.queryForObject("select snapshot::text from compensation_operation where order_id=?", String.class, order);
        assertFalse(snapshot.contains("private@example.test")); assertFalse(snapshot.contains("owner"));
        var done = store.complete(order, request.operationId(), claim.token(), proof(order));
        assertEquals("COMPENSATED", done.status()); assertNotNull(done.proofId());
        assertEquals(done.proofId(), store.begin(order, request).view().proofId());
        var different = new CompensationProofStore.Request(request.operationId(), 2, p, request.expectedPaymentNumber(), request.expectedAmount(), "USD", null);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> store.begin(order, different)).getStatusCode().value());
        assertThrows(Exception.class, () -> jdbc.update("UPDATE compensation_proof SET evidence='{}'::jsonb WHERE proof_id=?", done.proofId()));
        new TransactionTemplate(transactions).executeWithoutResult(s -> { guard.lock(order); guard.financialChange(order); });
        var held = store.view(order, request.operationId()); assertEquals("HOLD", held.status());
        assertEquals(done.proofVersion(), held.proofVersion()); assertTrue(held.financialVersion() > held.proofVersion());
    }

    @Test void preexistingHoldRemainsFencedAfterCompensation() {
        long order = 29104, p = payment(order);
        new TransactionTemplate(transactions).executeWithoutResult(s -> guard.hold(order));
        var r = request(order, p); var claim = store.begin(order, r);
        assertEquals("COMPENSATED", store.complete(order, r.operationId(), claim.token(), proof(order)).status());
        assertTrue(jdbc.queryForObject("select reconciliation_hold and permanent_fence from payment_order_guard where order_id=?", Boolean.class, order));
    }

    @Test void unsupportedVerifierResultsCompleteAsProviderUnverifiedHold() throws Exception {
        var verifier = new StripeCompensationVerifier();
        try (var fixture = new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifierTest.Fixture()) {
        for (int i = 0; i < 5; i++) {
            long order = 29300 + i, p = payment(order);
            var request = request(order, p); var claim = store.begin(order, request);
            var attempt = new StripeCompensationVerifier.Attempt(p, "proof-" + order, "pi_" + order,
                    i == 3 ? null : false, i != 2, new BigDecimal("25.00"), "USD", i == 0, i == 1 ? "VNPAY" : "STRIPE");
            var result = i == 4 ? verifier.verify("acct_foreign", List.of(attempt)) : fixture.verify(List.of(attempt));
            assertFalse(result.safe());
            var held = store.complete(order, request.operationId(), claim.token(), result);
            assertEquals("HOLD", held.status()); assertEquals("PROVIDER_UNVERIFIED", held.reason());
        }
        }
    }

    @Test void platformKeyCannotVerifyConnectedOnlyIntent() throws Exception {
        try (var fixture = new org.de013.paymentservice.gateway.stripe.StripeCompensationVerifierTest.Fixture()) {
            long order = 29310;
            long p = jdbc.queryForObject("INSERT INTO payments(payment_number,order_id,user_id,amount,currency,status,method,gateway_name,stripe_payment_intent_id,stripe_intent_livemode,stripe_intent_created_by_service) VALUES ('connected-fixture',?,'owner',25,'USD','SUCCEEDED','CARD','STRIPE','pi_connected',false,true) RETURNING id", Long.class, order);
            var r = new CompensationProofStore.Request(UUID.randomUUID(), 1, p, "connected-fixture", new BigDecimal("25.00"), "USD", null);
            var claim = store.begin(order, r);
            var attempt = new StripeCompensationVerifier.Attempt(p, "connected-fixture", "pi_connected", false, true, new BigDecimal("25.00"), "USD", false, "STRIPE");
            var result = fixture.verify(List.of(attempt));
            assertFalse(result.safe()); assertEquals("PROVIDER_OR_DATA_UNAVAILABLE", result.reason());
            assertEquals(1, fixture.connectedRequests.get()); assertFalse(fixture.hasViolation());
            var held = store.complete(order, r.operationId(), claim.token(), result);
            assertEquals("HOLD", held.status()); assertEquals("PROVIDER_UNVERIFIED", held.reason()); assertNull(held.proofId());
            assertTrue(jdbc.queryForObject("select permanent_fence from payment_order_guard where order_id=?", Boolean.class, order));
            assertEquals(0, jdbc.queryForObject("select count(*) from compensation_proof where operation_id=?", Integer.class, r.operationId()));
        }
    }

    @Test void leaseAndVersionAndUnresolvedEventsFailClosed() {
        long order = 29102, p = payment(order); var request = request(order, p); var claim = store.begin(order, request);
        jdbc.update("UPDATE compensation_operation SET claim_until=clock_timestamp()-interval '1 second' WHERE order_id=?", order);
        assertThrows(ResponseStatusException.class, () -> store.complete(order, request.operationId(), claim.token(), proof(order)));
        var renewed = store.begin(order, request); assertNotEquals(claim.token(), renewed.token());
        new TransactionTemplate(transactions).executeWithoutResult(s -> { guard.lock(order); guard.financialChange(order); });
        assertEquals("HOLD", store.complete(order, request.operationId(), renewed.token(), proof(order)).status());
        long order2 = 29103, p2 = payment(order2); var r2 = request(order2, p2); var c2 = store.begin(order2, r2);
        jdbc.update("INSERT INTO stripe_financial_observation(event_id,event_type) VALUES ('unknown-proof-test','charge.succeeded')");
        assertEquals("UNRESOLVED_FINANCIAL_OBSERVATION", store.complete(order2, r2.operationId(), c2.token(), proof(order2)).reason());
    }

    @Test void mismatchedCandidateFactsAndClosureRemainHold() {
        for (int i = 0; i < 5; i++) {
            long order = 29200 + i, p = payment(order);
            var request = new CompensationProofStore.Request(UUID.randomUUID(), 1, i == 0 ? p + 100000 : p,
                    i == 1 ? "wrong" : "proof-" + order, new BigDecimal(i == 2 ? "24.00" : "25.00"),
                    i == 3 ? "EUR" : "USD", i == 4 ? UUID.randomUUID() : null);
            var claim = store.begin(order, request);
            assertEquals("HOLD", store.complete(order, request.operationId(), claim.token(), proof(order)).status());
        }
    }

    @Test void snapshotMutationWithoutVersionIncrementIsRejected() {
        long order = 29210, p = payment(order); var request = request(order, p); var claim = store.begin(order, request);
        long version = claim.view().financialVersion();
        jdbc.update("UPDATE payments SET amount=24.00 WHERE id=?", p);
        assertEquals(version, jdbc.queryForObject("SELECT financial_version FROM payment_order_guard WHERE order_id=?", Long.class, order));
        var result = store.complete(order, request.operationId(), claim.token(), proof(order));
        assertEquals("HOLD", result.status()); assertEquals("FINANCIAL_FACTS_CHANGED", result.reason());
    }

    @Test void expiredClaimCanBeReconstructedWithoutNewOperation() {
        long order = 29211, p = payment(order); var request = request(order, p); var first = store.begin(order, request);
        jdbc.update("UPDATE compensation_operation SET claim_until=clock_timestamp()-interval '1 second' WHERE order_id=?", order);
        var reconstructed = new CompensationProofStore(jdbc, guard, mapper);
        var tx = new TransactionTemplate(transactions);
        var next = tx.execute(s -> reconstructed.begin(order, request));
        assertNotEquals(first.token(), next.token());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM compensation_operation WHERE order_id=?", Integer.class, order));
        assertTrue(jdbc.queryForObject("SELECT permanent_fence FROM payment_order_guard WHERE order_id=?", Boolean.class, order));
        assertEquals("COMPENSATED", tx.execute(s -> reconstructed.complete(order, request.operationId(), next.token(), proof(order))).status());
        assertNotNull(store.view(order, request.operationId()).proofId());
    }

    @Test void payloadAndProofRowsRejectMutation() {
        long order = 29212, p = payment(order); var request = request(order, p); var claim = store.begin(order, request);
        assertThrows(Exception.class, () -> jdbc.update("UPDATE compensation_operation SET finding_revision=2 WHERE order_id=?", order));
        var changedRevision = new CompensationProofStore.Request(request.operationId(), 2, p, request.expectedPaymentNumber(), request.expectedAmount(), request.expectedCurrency(), null);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> store.begin(order, changedRevision)).getStatusCode().value());
        var proof = store.complete(order, request.operationId(), claim.token(), proof(order));
        assertNotNull(proof.proofId());
        assertThrows(Exception.class, () -> jdbc.update("UPDATE compensation_proof SET evidence='{}'::jsonb WHERE proof_id=?", proof.proofId()));
        assertThrows(Exception.class, () -> jdbc.update("DELETE FROM compensation_proof WHERE proof_id=?", proof.proofId()));
        assertThrows(Exception.class, () -> jdbc.update("DELETE FROM compensation_history WHERE operation_id=?", request.operationId()));
    }
}
