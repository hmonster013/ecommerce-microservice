package org.de013.orderservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.de013.orderservice.client.InventoryReservationClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.*;

class InventoryKeycloakTest {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String base;
    private String admin;

    @Test void issuedServiceTokenHasContractClaimsAndIsUsedByFeign() throws Exception {
        String password = UUID.randomUUID().toString();
        String secret = UUID.randomUUID().toString();
        try (GenericContainer<?> keycloak = new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.0.7"))
                .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", password)
                .withCommand("start-dev")
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/realms/master").forStatusCode(200))) {
            keycloak.start();
            base = "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
            admin = json.readTree(form("/realms/master/protocol/openid-connect/token",
                    "grant_type=password&client_id=admin-cli&username=admin&password=" + password).body()).get("access_token").asText();
            api("POST", "/admin/realms", "{\"realm\":\"inventory-test\",\"enabled\":true}");
            String root = "/admin/realms/inventory-test";
            api("POST", root + "/clients", "{\"clientId\":\"product-catalog-service\",\"enabled\":true}");
            api("POST", root + "/clients", "{\"clientId\":\"order-service\",\"enabled\":true,\"publicClient\":false,\"serviceAccountsEnabled\":true,\"secret\":\"" + secret + "\"}");
            String catalog = api("GET", root + "/clients?clientId=product-catalog-service", null).get(0).get("id").asText();
            String order = api("GET", root + "/clients?clientId=order-service", null).get(0).get("id").asText();
            api("POST", root + "/clients/" + catalog + "/roles", "{\"name\":\"catalog-inventory-reservation\"}");
            JsonNode role = api("GET", root + "/clients/" + catalog + "/roles/catalog-inventory-reservation", null);
            String user = api("GET", root + "/clients/" + order + "/service-account-user", null).get("id").asText();
            api("POST", root + "/users/" + user + "/role-mappings/clients/" + catalog, "[" + role + "]");
            api("POST", root + "/clients/" + order + "/protocol-mappers/models", "{\"name\":\"catalog-audience\",\"protocol\":\"openid-connect\",\"protocolMapper\":\"oidc-audience-mapper\",\"config\":{\"included.client.audience\":\"product-catalog-service\",\"access.token.claim\":\"true\",\"id.token.claim\":\"false\"}}");

            AtomicReference<String> authorization = new AtomicReference<>();
            HttpServer stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            stub.createContext("/internal/inventory-reservations", exchange -> {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
            });
            stub.start();
            try {
                SpringApplication app = new SpringApplication(InventoryFeignSecurityTest.App.class);
                app.setWebApplicationType(WebApplicationType.SERVLET);
                try (var context = app.run(
                        "--server.port=0",
                        "--spring.flyway.enabled=false",
                        "--spring.cloud.openfeign.client.config.inventoryReservation.url=http://127.0.0.1:" + stub.getAddress().getPort(),
                        "--spring.cloud.openfeign.client.config.orderCart.url=http://127.0.0.1:" + stub.getAddress().getPort(),
                        "--spring.security.oauth2.client.registration.catalog-inventory.client-id=order-service",
                        "--spring.security.oauth2.client.registration.catalog-inventory.client-secret=" + secret,
                        "--spring.security.oauth2.client.registration.catalog-inventory.authorization-grant-type=client_credentials",
                        "--spring.security.oauth2.client.provider.catalog-inventory.token-uri=" + base + "/realms/inventory-test/protocol/openid-connect/token")) {
                    context.getBean(InventoryReservationClient.class).lookup(UUID.randomUUID());
                }
                assertNotNull(authorization.get());
                String token = authorization.get().substring("Bearer ".length());
                JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
                assertEquals("order-service", claims.path("azp").asText());
                assertTrue(contains(claims.path("aud"), "product-catalog-service"));
                assertTrue(contains(claims.path("resource_access").path("product-catalog-service").path("roles"), "catalog-inventory-reservation"));
            } finally { stub.stop(0); }
        }
    }

    private static boolean contains(JsonNode array, String value) {
        if (!array.isArray()) return false;
        for (JsonNode element : array) if (element.isTextual() && value.equals(element.textValue())) return true;
        return false;
    }

    private HttpResponse<String> form(String path, String body) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode()); return response;
    }

    private JsonNode api(String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                .header("Authorization", "Bearer " + admin).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertTrue(response.statusCode() >= 200 && response.statusCode() < 300, method + " " + path + " status " + response.statusCode());
        return response.body().isBlank() ? json.nullNode() : json.readTree(response.body());
    }
}
