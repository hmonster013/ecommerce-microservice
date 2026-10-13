package org.de013.userservice.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.de013.userservice.config.KeycloakProfileProperties;
import org.de013.userservice.dto.KeycloakProfile;
import org.de013.userservice.exception.ProfileSyncException;
import org.de013.userservice.service.KeycloakProfileClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class KeycloakProfileClientImpl implements KeycloakProfileClient {
    private final KeycloakProfileProperties config;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private String token;
    private Instant expiresAt = Instant.EPOCH;

    public KeycloakProfileClientImpl(KeycloakProfileProperties config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(config.getConnectTimeout()).build();
    }

    private static String encodeFormValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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

    private String base() {
        if (!StringUtils.hasText(config.getBaseUrl()) || !StringUtils.hasText(config.getRealm())
                || !StringUtils.hasText(config.getClientId()) || !StringUtils.hasText(config.getClientSecret())) {
            throw unavailable();
        }
        return config.getBaseUrl().replaceAll("/+$", "");
    }

    private synchronized String accessToken() {
        if (token != null && Instant.now().isBefore(expiresAt)) return token;
        String form = "grant_type=client_credentials&client_id=" + encodeFormValue(config.getClientId() == null ? "" : config.getClientId())
                + "&client_secret=" + encodeFormValue(config.getClientSecret() == null ? "" : config.getClientSecret());
        HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/realms/" + encodePathSegment(config.getRealm())
                        + "/protocol/openid-connect/token"))
                .timeout(config.getRequestTimeout()).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> response = send(request, false);
        if (response.statusCode() != 200) throw unavailable();
        JsonNode data = json(response.body());
        if (data == null || !data.isObject()) throw unavailable();
        if (!data.path("access_token").isTextual() || data.path("access_token").asText().isBlank()
                || !data.path("expires_in").canConvertToLong() || data.path("expires_in").asLong() <= 0) throw unavailable();
        token = data.path("access_token").asText();
        expiresAt = Instant.now().plusSeconds(Math.max(0, data.path("expires_in").asLong() - 30));
        return token;
    }

    private HttpRequest.Builder userRequest(String subject) {
        if (!StringUtils.hasText(subject)) throw new ProfileSyncException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_ERROR");
        String bearer = accessToken();
        return HttpRequest.newBuilder(URI.create(base() + "/admin/realms/" + encodePathSegment(config.getRealm()) + "/users/" + encodePathSegment(subject)))
                .timeout(config.getRequestTimeout()).header("Authorization", "Bearer " + bearer)
                .header("Accept", "application/json");
    }

    @Override
    public KeycloakProfile read(String subject) {
        HttpResponse<String> response = send(userRequest(subject).GET().build(), false);
        if (response.statusCode() == 404) throw new ProfileSyncException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
        if (response.statusCode() != 200) throw unavailable();
        JsonNode data = json(response.body());
        if (data == null || !data.isObject()) throw unavailable();
        if (!subject.equals(text(data, "id")) || !StringUtils.hasText(text(data, "username"))
                || !StringUtils.hasText(text(data, "email"))) throw unavailable();
        return new KeycloakProfile(subject, text(data, "username"), text(data, "email"), text(data, "firstName"), text(data, "lastName"));
    }

    @Override
    public void updateNames(String subject, String firstName, String lastName) {
        Map<String, String> payload = new HashMap<>();
        if (firstName != null) payload.put("firstName", firstName);
        if (lastName != null) payload.put("lastName", lastName);
        try {
            HttpRequest request = userRequest(subject).header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
            HttpResponse<String> response = send(request, true);
            if (response.statusCode() == 204) return;
            if (response.statusCode() == 400) {
                Map<String, String> fields = new HashMap<>();
                if (firstName != null) fields.put("firstName", "profile.invalidName");
                if (lastName != null) fields.put("lastName", "profile.invalidName");
                throw new ProfileSyncException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", fields);
            }
            if (response.statusCode() == 404) throw new ProfileSyncException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
            if (response.statusCode() == 409) throw new ProfileSyncException(HttpStatus.CONFLICT, "PROFILE_IDENTITY_CONFLICT");
            if (response.statusCode() >= 500) throw uncertain();
            throw unavailable();
        } catch (IOException ex) {
            throw unavailable();
        }
    }

    private HttpResponse<String> send(HttpRequest request, boolean mutation) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw mutation ? uncertain() : unavailable();
        } catch (IOException ex) {
            throw mutation ? uncertain() : unavailable();
        }
    }

    private JsonNode json(String body) {
        try { return mapper.readTree(body); }
        catch (IOException ex) { throw unavailable(); }
    }

    private static String text(JsonNode data, String key) {
        return data != null && data.path(key).isTextual() ? data.path(key).asText() : null;
    }

    private static ProfileSyncException unavailable() {
        return new ProfileSyncException(HttpStatus.SERVICE_UNAVAILABLE, "PROFILE_IDENTITY_UNAVAILABLE");
    }
    private static ProfileSyncException uncertain() {
        return new ProfileSyncException(HttpStatus.SERVICE_UNAVAILABLE, "PROFILE_UPDATE_UNCERTAIN");
    }
}
