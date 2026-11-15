package org.de013.productcatalog.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.de013.productcatalog.repository.CategoryRepository;
import org.de013.productcatalog.repository.CurrencySettingsRepository;
import org.de013.productcatalog.entity.CurrencySettings;
import org.de013.productcatalog.entity.CurrencyExchangeRate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogAuditSecurityTest {
    private static final RSAKey KEY = key();
    private static final HttpServer SERVER = server();
    private static final String ISSUER = "http://localhost:" + SERVER.getAddress().getPort();
    @Autowired MockMvc mvc;
    @Autowired CurrencySettingsRepository settings;
    @Autowired CategoryRepository categories;
    @MockBean(name = "cacheManager") CacheManager cacheManager;

    @BeforeEach
    void caches() {
        var local = new ConcurrentMapCacheManager();
        org.mockito.Mockito.when(cacheManager.getCache(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> local.getCache(invocation.getArgument(0)));
    }

    @DynamicPropertySource
    static void issuer(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
    }
    @AfterAll static void stop() { SERVER.stop(0); }

    private static RSAKey key() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate()).keyID("audit").build();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static HttpServer server() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                String issuer = "http://localhost:" + server.getAddress().getPort();
                String body = exchange.getRequestURI().getPath().equals("/jwks") ? "{\"keys\":[" + KEY.toPublicJWK().toJSONString() + "]}"
                        : "{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + issuer + "/jwks\"}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (var out = exchange.getResponseBody()) { out.write(bytes); }
            });
            server.start();
            return server;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private String token() throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(ISSUER).subject("verified-subject")
                .audience("product-catalog-service").expirationTime(new Date(System.currentTimeMillis() + 300000))
                .claim("realm_access", Map.of("roles", List.of("ADMIN"))).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("audit").build(), claims);
        jwt.sign(new RSASSASigner(KEY));
        return jwt.serialize();
    }

    @Test
    void persistedAuditUsesVerifiedSubjectNotHeaders() throws Exception {
        String bearer = "Bearer " + token();
        if (settings.findById(1L).isEmpty()) {
            var initial = new CurrencySettings();
            initial.setId(1L);
            initial.setBaseCurrency("USD");
            initial.setUpdatedAt(LocalDateTime.now());
            var usd = new CurrencyExchangeRate();
            usd.setCurrencyCode("USD");
            usd.setRate(BigDecimal.ONE);
            usd.setEnabled(true);
            usd.setSettings(initial);
            initial.getRates().add(usd);
            settings.saveAndFlush(initial);
        }
        long version = settings.findById(1L).orElseThrow().getVersion();
        mvc.perform(put("/settings/currency").header("Authorization", bearer).header("X-User-Id", "victim")
                        .header("X-User-Username", "victim").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + ",\"rates\":[{\"currency\":\"USD\",\"rate\":\"1\",\"enabled\":true}]}"))
                .andExpect(status().isOk());
        assertThat(settings.findById(1L).orElseThrow().getUpdatedBy()).isEqualTo("verified-subject");

        mvc.perform(post("/categories").header("Authorization", bearer).header("X-User-Id", "victim")
                        .header("X-User-Username", "victim").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Audit category\",\"slug\":\"audit-category\"}"))
                .andExpect(status().isCreated());
        var category = categories.findBySlug("audit-category").orElseThrow();
        assertThat(category.getCreatedBy()).isEqualTo("verified-subject");
        assertThat(category.getUpdatedBy()).isEqualTo("verified-subject");
    }
}
