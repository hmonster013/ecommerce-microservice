package org.de013.paymentservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.de013.paymentservice.gateway.stripe.StripeCompensationVerifier;
import org.de013.paymentservice.security.CheckoutClosureSecurityTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = {"spring.cloud.bus.enabled=false", "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompensationApiDefaultOffIntegrationTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", DB::getJdbcUrl); p.add("spring.datasource.username", DB::getUsername);
        p.add("spring.datasource.password", DB::getPassword); p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        p.add("payment.internal.issuer-uri", () -> CheckoutClosureSecurityTest.issuer);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CompensationProofStore store;
    @Autowired Environment environment;

    @Test void absentFlagDisablesPostWithoutFenceButExistingProofRemainsReadable() throws Exception {
        assertNull(environment.getProperty("payment.compensation.enabled"));
        long order = 30701;
        long payment = jdbc.queryForObject("insert into payments(payment_number,order_id,user_id,amount,currency,status,method,gateway_name,stripe_payment_intent_id,stripe_intent_livemode,stripe_intent_created_by_service) values ('off-30701',?, 'owner',25,'USD','SUCCEEDED','CARD','STRIPE','pi_off_30701',false,true) returning id", Long.class, order);
        UUID op = UUID.randomUUID();
        var request = new CompensationProofStore.Request(op, 1, payment, "off-30701", new BigDecimal("25.00"), "USD", null);
        var claim = store.begin(order, request);
        var evidence = new StripeCompensationVerifier.Evidence("acct_test", false, Instant.now(), List.of(new StripeCompensationVerifier.ChargeEvidence("ch_off", "pi_off_30701", 2500, "succeeded", List.of(new StripeCompensationVerifier.RefundEvidence("re_off", 2500, "succeeded")))));
        var proof = store.complete(order, op, claim.token(), new StripeCompensationVerifier.Result(true, null, evidence));
        assertEquals("COMPENSATED", proof.status());
        String resolve = CheckoutClosureSecurityTest.tokenWithRole(CheckoutClosureSecurityTest.issuer, "payment-service", "order-service", "payment-reconciliation-resolve", Instant.now().plusSeconds(300), false);
        String read = CheckoutClosureSecurityTest.tokenWithRole(CheckoutClosureSecurityTest.issuer, "payment-service", "order-service", "payment-reconciliation-read", Instant.now().plusSeconds(300), false);
        String body = "{\"operationId\":\"" + UUID.randomUUID() + "\",\"findingRevision\":1,\"expectedPaymentId\":1,\"expectedPaymentNumber\":\"off\",\"expectedAmount\":25,\"expectedCurrency\":\"USD\"}";
        mvc.perform(post("/internal/reconciliation/orders/30702/compensation-proofs").header("Authorization", "Bearer " + resolve).contentType("application/json").content(body)).andExpect(status().isServiceUnavailable());
        assertEquals(0, jdbc.queryForObject("select count(*) from payment_order_guard where order_id=30702", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from compensation_operation where order_id=30702", Integer.class));
        mvc.perform(get("/internal/reconciliation/orders/30701/compensation-proofs/" + op).header("Authorization", "Bearer " + read))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPENSATED")).andExpect(jsonPath("$.proofId").value(proof.proofId().toString()));
    }
}
