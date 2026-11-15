package org.de013.productcatalog.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@WebMvcTest(CatalogKeycloakSecurityTest.Endpoint.class)
@org.springframework.test.context.ActiveProfiles("keycloak-security-test")
@ContextConfiguration(classes = CatalogKeycloakSecurityTest.App.class)
class CatalogKeycloakSecurityTest {
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final String SECRET = UUID.randomUUID().toString();
    private static final String OTHER_SECRET = UUID.randomUUID().toString();
    private static final String USER_PASSWORD = UUID.randomUUID().toString();
    private static final String AUDIENCE = "product-catalog-service";
    private static final String ROLE = "catalog-inventory-reservation";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    @Container static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.0.7"))
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin").withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", PASSWORD)
            .withCommand("start-dev").withExposedPorts(8080).waitingFor(Wait.forHttp("/realms/master").forStatusCode(200));
    private static String base, admin, orderId, catalogId, serviceUser, role;
    @Autowired MockMvc mvc;

    @Configuration
    @Profile("keycloak-security-test")
    @EnableAutoConfiguration
    @Import({SecurityConfig.class, Endpoint.class})
    static class App { }

    @RestController
    @Profile("keycloak-security-test")
    static class Endpoint {
        @RequestMapping("/**") String handle() { return "ok"; }
    }

    @DynamicPropertySource
    static void issuer(DynamicPropertyRegistry registry) {
        try {
            base = "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
            admin = token("/realms/master", "grant_type=password&client_id=admin-cli&username=admin&password=" + PASSWORD);
            api("POST", "/admin/realms", "{\"realm\":\"catalog-test\",\"enabled\":true}");
            String root = "/admin/realms/catalog-test";
            api("POST", root + "/clients", "{\"clientId\":\"product-catalog-service\",\"enabled\":true}");
            client(root, "order-service", SECRET);
            client(root, "other-service", OTHER_SECRET);
            api("POST", root + "/clients", "{\"clientId\":\"test-user-client\",\"enabled\":true,\"publicClient\":true,\"directAccessGrantsEnabled\":true}");
            catalogId = id(root, "product-catalog-service");
            orderId = id(root, "order-service");
            String otherId = id(root, "other-service");
            String userClientId = id(root, "test-user-client");
            api("POST", root + "/clients/" + catalogId + "/roles", "{\"name\":\"" + ROLE + "\"}");
            role = api("GET", root + "/clients/" + catalogId + "/roles/" + ROLE, null).toString();
            serviceUser = api("GET", root + "/clients/" + orderId + "/service-account-user", null).path("id").asText();
            String otherUser = api("GET", root + "/clients/" + otherId + "/service-account-user", null).path("id").asText();
            api("POST", root + "/users/" + serviceUser + "/role-mappings/clients/" + catalogId, "[" + role + "]");
            api("POST", root + "/users/" + otherUser + "/role-mappings/clients/" + catalogId, "[" + role + "]");
            audience(root, orderId); audience(root, otherId); audience(root, userClientId);
            api("POST", root + "/roles", "{\"name\":\"ADMIN\"}");
            JsonNode adminRole = api("GET", root + "/roles/ADMIN", null);
            api("POST", root + "/users", "{\"username\":\"catalog-admin\",\"enabled\":true,\"email\":\"catalog-admin@example.invalid\",\"emailVerified\":true,\"firstName\":\"Catalog\",\"lastName\":\"Admin\",\"credentials\":[{\"type\":\"password\",\"value\":\"" + USER_PASSWORD + "\",\"temporary\":false}]}");
            String user = api("GET", root + "/users?username=catalog-admin&exact=true", null).get(0).path("id").asText();
            api("POST", root + "/users/" + user + "/role-mappings/realm", "[" + adminRole + "]");
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> base + "/realms/catalog-test");
        } catch (Exception e) { throw new IllegalStateException("Keycloak fixture setup failed", e); }
    }

    @Test
    void realKeycloakTokensEnforceAllInternalRoutes() throws Exception {
        String root = "/admin/realms/catalog-test";
        String valid = service("order-service", SECRET);
        String wrongAzp = service("other-service", OTHER_SECRET);
        String user = token("/realms/catalog-test", "grant_type=password&client_id=test-user-client&username=catalog-admin&password=" + USER_PASSWORD);
        assertClaims(valid, "order-service", true, true, false);
        assertClaims(wrongAzp, "other-service", true, true, false);
        assertClaims(user, "test-user-client", true, false, true);
        for (var route : routes()) {
            assertThat(call(route, valid)).isEqualTo(200);
            assertThat(call(route, wrongAzp)).isEqualTo(403);
            assertThat(call(route, user)).isEqualTo(403);
        }

        api("DELETE", root + "/users/" + serviceUser + "/role-mappings/clients/" + catalogId, "[" + role + "]");
        String missingRole = service("order-service", SECRET);
        assertClaims(missingRole, "order-service", true, false, false);
        for (var route : routes()) assertThat(call(route, missingRole)).isEqualTo(403);

        JsonNode mappers = api("GET", root + "/clients/" + orderId + "/protocol-mappers/models", null);
        for (JsonNode mapper : mappers) if ("catalog-audience".equals(mapper.path("name").asText()))
            api("DELETE", root + "/clients/" + orderId + "/protocol-mappers/models/" + mapper.path("id").asText(), null);
        String wrongAudience = service("order-service", SECRET);
        assertClaims(wrongAudience, "order-service", false, false, false);
        for (var route : routes()) assertThat(call(route, wrongAudience)).isEqualTo(401);
    }

    private record Route(HttpMethod method, String path) { }
    private static List<Route> routes() {
        return List.of(new Route(HttpMethod.POST, "/internal/inventory-reservations"),
                new Route(HttpMethod.GET, "/internal/inventory-reservations/op"),
                new Route(HttpMethod.POST, "/internal/inventory-reservations/op/release"),
                new Route(HttpMethod.POST, "/internal/inventory-reservations/op/fulfill"));
    }
    private int call(Route route, String token) throws Exception {
        return mvc.perform(request(route.method(), route.path()).header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
    }
    private static void assertClaims(String token, String azp, boolean audience, boolean role, boolean admin) throws Exception {
        JsonNode claims = JSON.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertThat(claims.path("iss").asText()).isEqualTo(base + "/realms/catalog-test");
        assertThat(claims.path("azp").asText()).isEqualTo(azp);
        assertThat(contains(claims.path("aud"), AUDIENCE)).isEqualTo(audience);
        assertThat(contains(claims.path("resource_access").path(AUDIENCE).path("roles"), ROLE)).isEqualTo(role);
        assertThat(contains(claims.path("realm_access").path("roles"), "ADMIN")).isEqualTo(admin);
    }
    private static boolean contains(JsonNode array, String value) {
        if (array.isTextual()) return value.equals(array.textValue());
        if (!array.isArray()) return false;
        for (JsonNode item : array) if (value.equals(item.asText())) return true;
        return false;
    }
    private static void client(String root, String name, String secret) throws Exception {
        api("POST", root + "/clients", "{\"clientId\":\"" + name + "\",\"enabled\":true,\"publicClient\":false,\"serviceAccountsEnabled\":true,\"directAccessGrantsEnabled\":false,\"secret\":\"" + secret + "\"}");
    }
    private static void audience(String root, String id) throws Exception {
        api("POST", root + "/clients/" + id + "/protocol-mappers/models", "{\"name\":\"catalog-audience\",\"protocol\":\"openid-connect\",\"protocolMapper\":\"oidc-audience-mapper\",\"config\":{\"included.client.audience\":\"product-catalog-service\",\"access.token.claim\":\"true\",\"id.token.claim\":\"false\"}}");
    }
    private static String id(String root, String client) throws Exception {
        return api("GET", root + "/clients?clientId=" + client, null).get(0).path("id").asText();
    }
    private static String service(String client, String secret) throws Exception {
        return token("/realms/catalog-test", "grant_type=client_credentials&client_id=" + client + "&client_secret=" + secret);
    }
    private static String token(String realm, String form) throws Exception {
        var response = HTTP.send(HttpRequest.newBuilder(URI.create(base + realm + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("token endpoint error: " + (response.statusCode() == 200 ? "none" : JSON.readTree(response.body()).path("error_description").asText())).isEqualTo(200);
        return JSON.readTree(response.body()).path("access_token").asText();
    }
    private static JsonNode api(String method, String path, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).header("Authorization", "Bearer " + admin)
                .header("Content-Type", "application/json").method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        var response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(method + " " + path).isBetween(200, 299);
        return response.body().isBlank() ? JSON.nullNode() : JSON.readTree(response.body());
    }
}
