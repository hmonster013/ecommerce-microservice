package org.de013.keycloak.profilesync.reconciliation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);

    private final String serverUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public KeycloakAdminClient(String serverUrl, String realm, String clientId, String clientSecret) {
        this(serverUrl, realm, clientId, clientSecret,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                new ObjectMapper());
    }

    public KeycloakAdminClient(String serverUrl, String realm, String clientId, String clientSecret,
                               HttpClient httpClient, ObjectMapper objectMapper) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public String obtainAccessToken() {
        try {
            String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", serverUrl, realm);

            String form = String.format("grant_type=client_credentials&client_id=%s&client_secret=%s",
                    URLEncoder.encode(clientId, StandardCharsets.UTF_8),
                    URLEncoder.encode(clientSecret, StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Failed to obtain service account token (HTTP {})", response.statusCode());
                throw new IllegalStateException("Authentication failed with Keycloak: HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            return root.path("access_token").asText();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while authenticating with Keycloak", e);
        } catch (IOException e) {
            throw new IllegalStateException("I/O error communicating with Keycloak: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> fetchUsers(String token, int first, int max) {
        try {
            String usersUrl = String.format("%s/admin/realms/%s/users?first=%d&max=%d", serverUrl, realm, first, max);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(usersUrl))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Failed to query Keycloak users (HTTP {})", response.statusCode());
                throw new IllegalStateException("Keycloak enumeration failed");
            }

            return objectMapper.readValue(response.body(), new TypeReference<List<Map<String, Object>>>() {});
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Keycloak enumeration interrupted", e);
        } catch (IOException e) {
            log.error("I/O error fetching users from Keycloak: {}", e.getMessage(), e);
            throw new IllegalStateException("Keycloak enumeration unavailable", e);
        }
    }
}
