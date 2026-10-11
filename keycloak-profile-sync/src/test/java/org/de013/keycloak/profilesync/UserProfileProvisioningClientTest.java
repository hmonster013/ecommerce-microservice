package org.de013.keycloak.profilesync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileProvisioningClientTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private ProfileSyncConfiguration configuration;
    private UserProfileProvisioningClient client;

    @BeforeEach
    void setUp() {
        configuration = new ProfileSyncConfiguration(
                "http://user-service:8081/internal/keycloak/users",
                "secret-123",
                "ecommerce-realm",
                Duration.ofSeconds(2),
                Duration.ofSeconds(5)
        );

        client = new UserProfileProvisioningClient(configuration, httpClient, new ObjectMapper());
    }

    @Test
    void provisionUserProfile_WhenHttp201Created_ShouldReturnTrue() throws Exception {
        UserProfilePayload payload = new UserProfilePayload(
                "kc-123", "alice", "alice@example.com", "Alice", "Wonder"
        );

        when(httpResponse.statusCode()).thenReturn(201);
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        boolean result = client.provisionUserProfile(payload);

        assertTrue(result);
    }

    @Test
    void provisionUserProfile_WhenHttp200Ok_ShouldReturnTrue() throws Exception {
        UserProfilePayload payload = new UserProfilePayload(
                "kc-123", "alice", "alice@example.com", "Alice", "Wonder"
        );

        when(httpResponse.statusCode()).thenReturn(200);
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        boolean result = client.provisionUserProfile(payload);

        assertTrue(result);
    }

    @Test
    void provisionUserProfile_WhenHttp409Conflict_ShouldReturnFalse() throws Exception {
        UserProfilePayload payload = new UserProfilePayload(
                "kc-123", "alice", "alice@example.com", "Alice", "Wonder"
        );

        when(httpResponse.statusCode()).thenReturn(409);
        when(httpResponse.body()).thenReturn("{\"code\":\"PROFILE_CONFLICT\"}");
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        boolean result = client.provisionUserProfile(payload);

        assertFalse(result);
    }

    @Test
    void provisionUserProfile_WhenHttp500_ShouldReturnFalse() throws Exception {
        UserProfilePayload payload = new UserProfilePayload(
                "kc-123", "alice", "alice@example.com", "Alice", "Wonder"
        );

        when(httpResponse.statusCode()).thenReturn(500);
        when(httpResponse.body()).thenReturn("Internal Server Error");
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        boolean result = client.provisionUserProfile(payload);

        assertFalse(result);
    }

    @Test
    void provisionUserProfile_WhenNetworkThrowsIOException_ShouldReturnFalse() throws Exception {
        UserProfilePayload payload = new UserProfilePayload(
                "kc-123", "alice", "alice@example.com", "Alice", "Wonder"
        );

        when(httpClient.send(any(HttpRequest.class), any())).thenThrow(new IOException("Connection refused"));

        boolean result = client.provisionUserProfile(payload);

        assertFalse(result);
    }

    @Test
    void provisionUserProfile_WhenPayloadNullOrMissingKeycloakId_ShouldReturnFalse() {
        assertFalse(client.provisionUserProfile(null));
        assertFalse(client.provisionUserProfile(new UserProfilePayload(null, "u", "e", "f", "l")));
        assertFalse(client.provisionUserProfile(new UserProfilePayload("", "u", "e", "f", "l")));
    }
}
