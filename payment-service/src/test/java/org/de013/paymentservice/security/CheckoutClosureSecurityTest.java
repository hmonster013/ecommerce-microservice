package org.de013.paymentservice.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.*;
import java.time.Instant;
import java.util.*;
import org.de013.paymentservice.config.SecurityConfig;
import org.de013.paymentservice.controller.CheckoutClosureController;
import org.de013.paymentservice.service.CheckoutClosureService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({CheckoutClosureController.class, org.de013.paymentservice.controller.ReconciliationController.class})
@org.springframework.test.context.ContextConfiguration(classes = {CheckoutClosureController.class, org.de013.paymentservice.controller.ReconciliationController.class, SecurityConfig.class, HeaderAuthenticationFilter.class})
@Import({SecurityConfig.class, HeaderAuthenticationFilter.class})
public class CheckoutClosureSecurityTest {
    static final HttpServer server;
    static final RSAKey key;
    public static final String issuer;
    static {
        try {
            var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
            var pair = generator.generateKeyPair();
            key = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate()).keyID("test-key").build();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "/realms/test";
            server.createContext("/realms/test/.well-known/openid-configuration", e -> {
                byte[] data = ("{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + issuer + "/keys\"}").getBytes(StandardCharsets.UTF_8);
                e.getResponseHeaders().set("Content-Type", "application/json"); e.sendResponseHeaders(200, data.length); e.getResponseBody().write(data); e.close();
            });
            server.createContext("/realms/test/keys", e -> {
                byte[] data = ("{\"keys\":[" + key.toPublicJWK().toJSONString() + "]}").getBytes(StandardCharsets.UTF_8);
                e.getResponseHeaders().set("Content-Type", "application/json"); e.sendResponseHeaders(200, data.length); e.getResponseBody().write(data); e.close();
            });
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r -> { var t = new Thread(r); t.setDaemon(true); return t; }));
            server.start();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource static void issuer(DynamicPropertyRegistry p) { p.add("payment.internal.issuer-uri", () -> issuer); }
    @Autowired MockMvc mvc;
    @MockBean CheckoutClosureService closures;
    @MockBean org.springframework.jdbc.core.JdbcTemplate jdbc;

    public static String token(String iss, String audience, String azp, boolean role, Instant expiration, boolean wrongKey) throws Exception {
        return tokenWithRole(iss, audience, azp, role ? "payment-checkout-close" : "", expiration, wrongKey);
    }
    public static String tokenWithRole(String iss, String audience, String azp, String role, Instant expiration, boolean wrongKey) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(iss).subject("service-account-order-service")
                .issueTime(Date.from(Instant.now())).audience(audience).claim("azp", azp)
                .claim("resource_access", Map.of("payment-service", Map.of("roles", List.of(role))))
                .claim("realm_access", Map.of("roles", List.of("ADMIN")));
        if (expiration != null) claims.expirationTime(Date.from(expiration));
        var signingKey = key;
        if (wrongKey) {
            var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); var pair = generator.generateKeyPair();
            signingKey = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate()).keyID("test-key").build();
        }
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims.build());
        jwt.sign(new RSASSASigner(signingKey)); return jwt.serialize();
    }

    @Test void reconciliationRoleIsIndependent() throws Exception {
        String read = tokenWithRole(issuer, "payment-service", "order-service", "payment-reconciliation-read", Instant.now().plusSeconds(300), false);
        String close = token(issuer, "payment-service", "order-service", true, Instant.now().plusSeconds(300), false);
        String wrongAudience = tokenWithRole(issuer, "other", "order-service", "payment-reconciliation-read", Instant.now().plusSeconds(300), false);
        mvc.perform(get("/internal/reconciliation/orders/42").header("Authorization", "Bearer " + read)).andExpect(status().isOk());
        mvc.perform(get("/internal/reconciliation/orders/42").header("Authorization", "Bearer " + close)).andExpect(status().isForbidden());
        mvc.perform(get("/internal/reconciliation/orders/42").header("Authorization", "Bearer " + wrongAudience)).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/reconciliation/orders/42").header("X-User-Id", "fake")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/checkout-closures/42").header("Authorization", "Bearer " + read)).andExpect(status().isForbidden());
    }

    @Test void validatesRealSignaturesClaimsAndRoutes() throws Exception {
        var op = UUID.randomUUID();
        when(closures.close(eq(42L), eq(op))).thenReturn(new CheckoutClosureService.Result(42L, op, "SAFE", null));
        String valid = token(issuer, "payment-service", "order-service", true, Instant.now().plusSeconds(300), false);
        String body = "{\"operationId\":\"" + op + "\"}";
        mvc.perform(post("/internal/checkout-closures/42").header("Authorization", "Bearer " + valid).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SAFE"));
        mvc.perform(post("/internal/checkout-closures/42").header("X-User-Id", "fake").header("X-User-Username", "admin").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        for (String invalid : List.of(token(issuer, "other", "order-service", true, Instant.now().plusSeconds(300), false),
                token("https://wrong.test", "payment-service", "order-service", true, Instant.now().plusSeconds(300), false),
                token(issuer, "payment-service", "order-service", true, Instant.now().minusSeconds(300), false),
                token(issuer, "payment-service", "order-service", true, null, false),
                token(issuer, "payment-service", "other", true, Instant.now().plusSeconds(300), false),
                token(issuer, "payment-service", "order-service", true, Instant.now().plusSeconds(300), true)))
            mvc.perform(post("/internal/checkout-closures/42").header("Authorization", "Bearer " + invalid).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        String noRole = token(issuer, "payment-service", "order-service", false, Instant.now().plusSeconds(300), false);
        mvc.perform(post("/internal/checkout-closures/42").header("Authorization", "Bearer " + noRole).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(options("/internal/checkout-closures/42").header("Authorization", "Bearer " + valid)).andExpect(status().isForbidden());
        mvc.perform(post("/internal/other").header("Authorization", "Bearer " + valid)).andExpect(status().isForbidden());
        mvc.perform(post("/internal/checkout-closures/42").header("Authorization", "Bearer " + valid).contentType("application/json").content("{\"operationId\":\"not-a-uuid\"}")).andExpect(status().isBadRequest());
    }
}
