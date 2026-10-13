package org.de013.keycloak.profilesync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class UserProfileProvisioningClient {

    private static final Logger log = LoggerFactory.getLogger(UserProfileProvisioningClient.class);
    private static final String SECRET_HEADER = "X-Profile-Provisioner-Secret";

    private final HttpClient httpClient;
    private final ProfileSyncConfiguration configuration;
    private final ObjectMapper objectMapper;

    public UserProfileProvisioningClient(ProfileSyncConfiguration configuration) {
        this(configuration, HttpClient.newBuilder()
                .connectTimeout(configuration.getConnectTimeout())
                .build(), new ObjectMapper());
    }

    public UserProfileProvisioningClient(ProfileSyncConfiguration configuration, HttpClient httpClient, ObjectMapper objectMapper) {
        this.configuration = configuration;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public boolean provisionUserProfile(UserProfilePayload payload) {
        if (payload == null || payload.keycloakId() == null || payload.keycloakId().isBlank()) {
            log.warn("Cannot provision user profile: missing payload or keycloakId");
            return false;
        }

        try {
            String jsonBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(configuration.getUserServiceUrl()))
                    .timeout(configuration.getRequestTimeout())
                    .header("Content-Type", "application/json")
                    .header(SECRET_HEADER, configuration.getSharedSecret())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status == 200 || status == 201) {
                log.info("Profile provisioned successfully (status {}) for user {}", status, payload.keycloakId());
                return true;
            } else {
                log.warn("Profile provisioning failed with HTTP {} for user {}: {}", status, payload.keycloakId(), response.body());
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Profile provisioning interrupted for user {}", payload.keycloakId(), e);
            return false;
        } catch (IOException e) {
            log.error("Network or I/O error provisioning user {} to {}: {}",
                    payload.keycloakId(), configuration.getUserServiceUrl(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error provisioning user {}", payload.keycloakId(), e);
            return false;
        }
    }

    public String syncUserProfile(String subject) {
        if (subject == null || subject.isBlank()) return "failure";
        try {
            String segment = encodePathSegment(subject);
            HttpRequest request = HttpRequest.newBuilder(URI.create(configuration.getUserServiceUrl().replaceAll("/+$", "")
                            + "/" + segment + "/sync"))
                    .timeout(configuration.getRequestTimeout()).header(SECRET_HEADER, configuration.getSharedSecret())
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 409) return "conflict";
            if (response.statusCode() != 200) {
                log.warn("Identity sync failed: subject={}, status={}", subject, response.statusCode());
                return "failure";
            }
            var data = objectMapper.readTree(response.body());
            String outcome = data.path("data").path("outcome").asText();
            return data.path("success").asBoolean() && Set.of("created", "updated", "unchanged").contains(outcome)
                    ? outcome : "failure";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "failure";
        } catch (Exception ex) {
            log.warn("Identity sync unavailable for subject={}", subject);
            return "failure";
        }
    }

    private static String encodePathSegment(String value) {
        StringBuilder encoded = new StringBuilder();
        for (byte valueByte : value.getBytes(StandardCharsets.UTF_8)) {
            int character = valueByte & 0xff;
            if ((character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || character == '-' || character == '.' || character == '_' || character == '~') {
                encoded.append((char) character);
            } else {
                encoded.append('%').append(Character.toUpperCase(Character.forDigit(character >>> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(character & 0xf, 16)));
            }
        }
        return encoded.toString();
    }
}
