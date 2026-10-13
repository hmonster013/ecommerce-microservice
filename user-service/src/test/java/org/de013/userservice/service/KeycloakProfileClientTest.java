package org.de013.userservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.de013.userservice.config.KeycloakProfileProperties;
import org.de013.userservice.exception.ProfileSyncException;
import org.de013.userservice.service.impl.KeycloakProfileClientImpl;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class KeycloakProfileClientTest {
    @Test
    void cachesTokenAndUpdatesOnlyNames() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        AtomicInteger tokens = new AtomicInteger();
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/realms/test/protocol/openid-connect/token", e -> {
            tokens.incrementAndGet();
            byte[] response = "{\"access_token\":\"test-token\",\"expires_in\":300}".getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(200, response.length); e.getResponseBody().write(response); e.close();
        });
        server.createContext("/admin/realms/test/users/subject", e -> {
            if (e.getRequestMethod().equals("PUT")) {
                body.set(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                e.sendResponseHeaders(204, -1);
            } else {
                byte[] response = "{\"id\":\"subject\",\"username\":\"alice\",\"email\":\"alice@mail.net\"}".getBytes(StandardCharsets.UTF_8);
                e.sendResponseHeaders(200, response.length); e.getResponseBody().write(response);
            }
            e.close();
        });
        server.start();
        try {
            KeycloakProfileProperties config = new KeycloakProfileProperties();
            config.setBaseUrl("http://localhost:" + server.getAddress().getPort());
            config.setRealm("test"); config.setClientId("profile"); config.setClientSecret("test-only");
            ObjectMapper mapper = new ObjectMapper();
            KeycloakProfileClient client = new KeycloakProfileClientImpl(config, mapper);
            assertEquals("subject", client.read("subject").id());
            client.updateNames("subject", "An", "");
            client.read("subject");
            assertEquals(1, tokens.get());
            assertEquals(mapper.readTree("{\"firstName\":\"An\",\"lastName\":\"\"}"), mapper.readTree(body.get()));
        } finally { server.stop(0); }
    }

    @Test
    void missingBackendCredentialFailsClosed() {
        KeycloakProfileClient client = new KeycloakProfileClientImpl(new KeycloakProfileProperties(), new ObjectMapper());
        ProfileSyncException ex = assertThrows(ProfileSyncException.class, () -> client.read("subject"));
        assertEquals("PROFILE_IDENTITY_UNAVAILABLE", ex.getCode());
    }
}
