package org.de013.productcatalog.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

@WebMvcTest(CatalogSecurityTest.Endpoint.class)
@org.springframework.test.context.ActiveProfiles("catalog-security-test")
@ContextConfiguration(classes = CatalogSecurityTest.TestApplication.class)
class CatalogSecurityTest {
    @Configuration
    @Profile("catalog-security-test")
    @EnableAutoConfiguration
    @Import({SecurityConfig.class, Endpoint.class})
    static class TestApplication { }
    private static final RSAKey KEY = key("catalog-test");
    private static final RSAKey OTHER_KEY = key("catalog-test");
    private static final HttpServer SERVER = server();
    private static final String ISSUER = "http://localhost:" + SERVER.getAddress().getPort();
    private static final String AUDIENCE = "product-catalog-service";
    @Autowired MockMvc mvc;
    @Autowired RequestMappingHandlerAdapter handlerAdapter;

    @DynamicPropertySource
    static void issuer(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("management.server.port", () -> "9010");
        registry.add("server.port", () -> "8082");
    }

    @AfterAll
    static void stop() { SERVER.stop(0); }

    private static RSAKey key(String id) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate()).keyID(id).build();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static HttpServer server() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                String issuer = "http://localhost:" + server.getAddress().getPort();
                String body = exchange.getRequestURI().getPath().equals("/jwks")
                        ? "{\"keys\":[" + KEY.toPublicJWK().toJSONString() + "]}"
                        : "{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + issuer + "/jwks\"}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
            });
            server.start();
            return server;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String token(String issuer, String audience, Instant expiration, String azp,
                                List<String> realmRoles, Map<String, Object> resources, RSAKey key) throws Exception {
        var builder = new JWTClaimsSet.Builder().issuer(issuer).subject("real-subject")
                .claim("preferred_username", "real-name").claim("email", "real@example.test")
                .issueTime(new Date()).claim("realm_access", Map.of("roles", realmRoles))
                .claim("resource_access", resources);
        if (audience != null) builder.audience(audience);
        if (expiration != null) builder.expirationTime(Date.from(expiration));
        if (azp != null) builder.claim("azp", azp);
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), builder.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static String token(List<String> roles, String azp, Map<String, Object> resources) throws Exception {
        return token(ISSUER, AUDIENCE, Instant.now().plusSeconds(300), azp, roles, resources, KEY);
    }

    private int call(HttpMethod method, String path, String bearer) throws Exception {
        var request = request(method, path);
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test
    void publicAndDeniedRoutes() throws Exception {
        for (String path : List.of("/products", "/products/1", "/categories/tree", "/search", "/images/1", "/variants/1", "/exchange-rates", "/actuator/health", "/actuator/health/readiness"))
            assertThat(call(HttpMethod.GET, path, null)).as(path).isEqualTo(200);
        for (String path : List.of("/unknown", "/products/1/unknown", "/actuator/info", "/actuator/metrics", "/actuator/prometheus", "/swagger-ui/index.html", "/v3/api-docs"))
            assertThat(call(HttpMethod.GET, path, null)).as(path).isIn(401, 403);
        assertThat(call(HttpMethod.POST, "/unknown", null)).isIn(401, 403);
        assertThat(call(HttpMethod.OPTIONS, "/products", null)).isIn(401, 403);
        assertThat(call(HttpMethod.GET, "/products", "invalid")).isEqualTo(401);
    }

    @Test
    void mvcUsesJackson2ForMoneyDateAndNull() throws Exception {
        var converter = handlerAdapter.getMessageConverters().stream()
                .filter(c -> c.canWrite(JsonFixture.class, org.springframework.http.MediaType.APPLICATION_JSON))
                .findFirst().orElseThrow();
        assertThat(converter).isInstanceOf(MappingJackson2HttpMessageConverter.class);
        mvc.perform(request(HttpMethod.GET, "/products/json-fixture"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"price\":12.50,\"date\":\"2024-03-15\",\"nullable\":null}"));
    }

    @Test
    void prometheusIsOnlyPublicOnManagementPort() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/actuator/prometheus").with(r -> { r.setLocalPort(9010); return r; }))
                .andExpect(status().isOk());
        mvc.perform(request(HttpMethod.GET, "/actuator/prometheus").with(r -> { r.setLocalPort(8082); return r; }))
                .andExpect(status().isUnauthorized());
        mvc.perform(request(HttpMethod.GET, "/actuator/metrics").with(r -> { r.setLocalPort(9010); return r; }))
                .andExpect(status().isUnauthorized());
        mvc.perform(request(HttpMethod.POST, "/actuator/prometheus").with(r -> { r.setLocalPort(9010); return r; }))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void realmRolesAndExplicitMutationPolicy() throws Exception {
        String user = token(List.of("USER"), null, Map.of());
        String admin = token(List.of("ADMIN"), null, Map.of());
        String manager = token(List.of("MANAGER"), null, Map.of());
        String clientAdmin = token(List.of(), null, Map.of("other-client", Map.of("roles", List.of("ADMIN")), AUDIENCE, Map.of("roles", List.of("ADMIN"))));
        for (String path : List.of("/settings/currency", "/products", "/categories")) {
            HttpMethod method = path.equals("/settings/currency") ? HttpMethod.PUT : HttpMethod.POST;
            assertThat(call(method, path, admin)).as(path).isEqualTo(200);
            for (String denied : List.of(user, manager, clientAdmin)) assertThat(call(method, path, denied)).isEqualTo(403);
        }
        assertThat(call(HttpMethod.GET, "/settings/currency", admin)).isEqualTo(200);
        assertThat(call(HttpMethod.GET, "/settings/currency", user)).isEqualTo(403);
        for (String path : List.of("/products/1/inventory/add", "/variants/1/inventory/remove", "/products/1/inventory/set")) {
            assertThat(call(HttpMethod.POST, path, manager)).isEqualTo(200);
            assertThat(call(HttpMethod.POST, path, admin)).isEqualTo(200);
            assertThat(call(HttpMethod.POST, path, user)).isEqualTo(403);
        }
        for (String path : List.of("/products/1/inventory/reserve", "/variants/1/inventory/release", "/variants/1/inventory/fulfill", "/products/batch"))
            assertThat(call(HttpMethod.POST, path, admin)).isEqualTo(403);
    }

    @Test
    void internalRequiresExactClientAndRole() throws Exception {
        Map<String, Object> role = Map.of(AUDIENCE, Map.of("roles", List.of("catalog-inventory-reservation")));
        String service = token(List.of(), "order-service", role);
        for (var entry : Map.of("/internal/inventory-reservations", HttpMethod.POST, "/internal/inventory-reservations/op", HttpMethod.GET,
                "/internal/inventory-reservations/op/release", HttpMethod.POST, "/internal/inventory-reservations/op/fulfill", HttpMethod.POST).entrySet()) {
            assertThat(call(entry.getValue(), entry.getKey(), service)).isEqualTo(200);
            for (String denied : List.of(token(List.of("ADMIN"), null, Map.of()), token(List.of(), "other-service", role),
                    token(List.of(), "order-service", Map.of("other-client", Map.of("roles", List.of("catalog-inventory-reservation")))),
                    token(List.of(), "order-service", Map.of())))
                assertThat(call(entry.getValue(), entry.getKey(), denied)).isEqualTo(403);
        }
        assertThat(call(HttpMethod.POST, "/internal/inventory-reservations/op/other", service)).isEqualTo(403);
        assertThat(call(HttpMethod.POST, "/internal/inventory-reservations", token(ISSUER, "wrong", Instant.now().plusSeconds(300), "order-service", List.of(), role, KEY))).isEqualTo(401);
    }

    @Test
    void verifiesSignedJwtAndRequiredClaims() throws Exception {
        assertThat(call(HttpMethod.GET, "/products", token(List.of(), null, Map.of()))).isEqualTo(200);
        for (String bad : List.of(
                token("https://wrong.example.test", AUDIENCE, Instant.now().plusSeconds(300), null, List.of(), Map.of(), KEY),
                token(ISSUER, "wrong", Instant.now().plusSeconds(300), null, List.of(), Map.of(), KEY),
                token(ISSUER, null, Instant.now().plusSeconds(300), null, List.of(), Map.of(), KEY),
                token(ISSUER, AUDIENCE, Instant.now().plusSeconds(300), null, List.of(), Map.of(), OTHER_KEY),
                token(ISSUER, AUDIENCE, Instant.now().minusSeconds(300), null, List.of(), Map.of(), KEY),
                token(ISSUER, AUDIENCE, null, null, List.of(), Map.of(), KEY)))
            assertThat(call(HttpMethod.GET, "/products", bad)).isEqualTo(401);
        assertThatThrownBy(() -> new SecurityConfig().jwtDecoder("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("issuer-uri is required");
    }

    @Test
    void missingIssuerFailsContextStartup() {
        new ApplicationContextRunner().withBean("jwtDecoder", org.springframework.security.oauth2.jwt.JwtDecoder.class,
                        () -> new SecurityConfig().jwtDecoder(""))
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining("issuer-uri is required"));
    }

    @Test
    void headersCannotAuthenticateOrReplaceJwtIdentity() throws Exception {
        mvc.perform(request(HttpMethod.POST, "/products").header("X-User-Id", "spoof").header("X-User-Username", "admin"))
                .andExpect(status().isUnauthorized());
        String admin = token(List.of("ADMIN"), null, Map.of());
        String response = mvc.perform(request(HttpMethod.POST, "/products").header("Authorization", "Bearer " + admin)
                        .header("X-User-Id", "spoof").header("X-User-Username", "spoof").header("X-User-Email", "spoof@example.test"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(response).isEqualTo("real-subject|real-name|real@example.test");
    }

    @RestController
    @Profile("catalog-security-test")
    static class Endpoint {
        @GetMapping("/products/json-fixture")
        JsonFixture jsonFixture() {
            return new JsonFixture(new BigDecimal("12.50"), LocalDate.of(2024, 3, 15), null);
        }

        @RequestMapping("/**")
        String handle(Authentication authentication) {
            if (authentication instanceof JwtAuthenticationToken token)
                return token.getToken().getSubject() + "|" + token.getToken().getClaimAsString("preferred_username") + "|" + token.getToken().getClaimAsString("email");
            return "public";
        }
    }

    record JsonFixture(BigDecimal price, LocalDate date, String nullable) { }
}
