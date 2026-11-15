package org.de013.paymentservice.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.de013.paymentservice.config.SecurityConfig;
import org.de013.paymentservice.controller.CheckoutClosureController;
import org.de013.paymentservice.service.CheckoutClosureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@WebMvcTest(CheckoutClosureController.class)
@org.springframework.test.context.ContextConfiguration(classes = {CheckoutClosureController.class, SecurityConfig.class, HeaderAuthenticationFilter.class})
@Import({SecurityConfig.class, HeaderAuthenticationFilter.class})
class CheckoutClosureKeycloakTest {
    static final String PASSWORD = UUID.randomUUID().toString();
    static final String SECRET = UUID.randomUUID().toString();
    static final String OTHER_SECRET = UUID.randomUUID().toString();
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newHttpClient();
    @Container static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.0.7"))
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin").withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", PASSWORD)
            .withCommand("start-dev").withExposedPorts(8080).waitingFor(Wait.forHttp("/realms/master").forStatusCode(200));
    static String base, admin, paymentId, orderId, otherId;
    @Autowired MockMvc mvc;
    @MockBean CheckoutClosureService closures;

    @DynamicPropertySource static void setup(DynamicPropertyRegistry registry) {
        try {
            base = "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
            admin = token("master", "grant_type=password&client_id=admin-cli&username=admin&password=" + PASSWORD);
            api("POST", "/admin/realms", "{\"realm\":\"payment-test\",\"enabled\":true}");
            String root = "/admin/realms/payment-test";
            api("POST", root + "/clients", "{\"clientId\":\"payment-service\",\"enabled\":true}");
            client(root, "order-service", SECRET); client(root, "other-service", OTHER_SECRET);
            paymentId = id(root, "payment-service"); orderId = id(root, "order-service"); otherId = id(root, "other-service");
            api("POST", root + "/clients/" + paymentId + "/roles", "{\"name\":\"payment-checkout-close\"}");
            JsonNode role = api("GET", root + "/clients/" + paymentId + "/roles/payment-checkout-close", null);
            for (String client : List.of(orderId, otherId)) {
                String user = api("GET", root + "/clients/" + client + "/service-account-user", null).path("id").asText();
                api("POST", root + "/users/" + user + "/role-mappings/clients/" + paymentId, "[" + role + "]");
                api("POST", root + "/clients/" + client + "/protocol-mappers/models", "{\"name\":\"payment-audience\",\"protocol\":\"openid-connect\",\"protocolMapper\":\"oidc-audience-mapper\",\"config\":{\"included.client.audience\":\"payment-service\",\"access.token.claim\":\"true\",\"id.token.claim\":\"false\"}}");
            }
            registry.add("payment.internal.issuer-uri", () -> base + "/realms/payment-test");
        } catch (Exception e) { throw new IllegalStateException("Keycloak fixture setup failed", e); }
    }

    @Test void keycloakTokensUseProductionDecoderAndEnforceClaims() throws Exception {
        String good = service("order-service", SECRET), wrongAzp = service("other-service", OTHER_SECRET);
        claims(good, "order-service", true, true); claims(wrongAzp, "other-service", true, true);
        var op = UUID.randomUUID(); when(closures.close(eq(42L), eq(op))).thenReturn(new CheckoutClosureService.Result(42L, op, "SAFE", null));
        call(good, op, 200); call(wrongAzp, op, 401);
        String root = "/admin/realms/payment-test";
        String user = api("GET", root + "/clients/" + orderId + "/service-account-user", null).path("id").asText();
        JsonNode role = api("GET", root + "/clients/" + paymentId + "/roles/payment-checkout-close", null);
        api("DELETE", root + "/users/" + user + "/role-mappings/clients/" + paymentId, "[" + role + "]");
        String noRole = service("order-service", SECRET); claims(noRole, "order-service", true, false); call(noRole, op, 403);
        JsonNode mappers = api("GET", root + "/clients/" + orderId + "/protocol-mappers/models", null);
        for (JsonNode mapper : mappers) if ("payment-audience".equals(mapper.path("name").asText()))
            api("DELETE", root + "/clients/" + orderId + "/protocol-mappers/models/" + mapper.path("id").asText(), null);
        String noAudience = service("order-service", SECRET); claims(noAudience, "order-service", false, false); call(noAudience, op, 401);
    }

    void call(String jwt, UUID op, int expected) throws Exception {
        mvc.perform(post("/internal/checkout-closures/42").header("Authorization", "Bearer " + jwt)
                .contentType("application/json").content("{\"operationId\":\"" + op + "\"}"))
                .andExpect(status().is(expected));
    }
    static void claims(String token, String azp, boolean audience, boolean role) throws Exception {
        JsonNode claims = JSON.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertEquals(azp, claims.path("azp").asText());
        assertEquals(audience, contains(claims.path("aud"), "payment-service"));
        assertEquals(role, contains(claims.path("resource_access").path("payment-service").path("roles"), "payment-checkout-close"));
    }
    static boolean contains(JsonNode node, String value) {
        if (node.isTextual()) return value.equals(node.asText());
        if (node.isArray()) for (JsonNode element : node) if (element.isTextual() && value.equals(element.asText())) return true;
        return false;
    }
    static void client(String root, String name, String secret) throws Exception {
        api("POST", root + "/clients", "{\"clientId\":\"" + name + "\",\"enabled\":true,\"publicClient\":false,\"serviceAccountsEnabled\":true,\"secret\":\"" + secret + "\"}");
    }
    static String id(String root, String client) throws Exception { return api("GET", root + "/clients?clientId=" + client, null).get(0).path("id").asText(); }
    static String service(String client, String secret) throws Exception { return token("payment-test", "grant_type=client_credentials&client_id=" + client + "&client_secret=" + secret); }
    static String token(String realm, String body) throws Exception {
        var response = HTTP.send(HttpRequest.newBuilder(URI.create(base + "/realms/" + realm + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode()); return JSON.readTree(response.body()).path("access_token").asText();
    }
    static JsonNode api(String method, String path, String body) throws Exception {
        var response = HTTP.send(HttpRequest.newBuilder(URI.create(base + path)).header("Authorization", "Bearer " + admin)
                .header("Content-Type", "application/json").method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertTrue(response.statusCode() >= 200 && response.statusCode() < 300, method + " " + path + " status " + response.statusCode());
        return response.body().isBlank() ? JSON.nullNode() : JSON.readTree(response.body());
    }
}
