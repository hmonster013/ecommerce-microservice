package org.de013.keycloak.profilesync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class UserProfileProvisioningClient {

    private static final Logger log = Logger.getLogger(UserProfileProvisioningClient.class);
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
                log.infof("Profile provisioned successfully (status %d) for user %s", status, payload.keycloakId());
                return true;
            } else {
                log.warnf("Profile provisioning failed with HTTP %d for user %s: %s", status, payload.keycloakId(), response.body());
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.errorf(e, "Profile provisioning interrupted for user %s", payload.keycloakId());
            return false;
        } catch (IOException e) {
            log.errorf("Network or I/O error provisioning user %s to %s: %s",
                    payload.keycloakId(), configuration.getUserServiceUrl(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.errorf(e, "Unexpected error provisioning user %s", payload.keycloakId());
            return false;
        }
    }
}
