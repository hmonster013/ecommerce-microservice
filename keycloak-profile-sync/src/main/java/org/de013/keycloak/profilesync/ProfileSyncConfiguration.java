package org.de013.keycloak.profilesync;

import org.keycloak.Config;

import java.time.Duration;

public class ProfileSyncConfiguration {

    private final String userServiceUrl;
    private final String sharedSecret;
    private final String targetRealm;
    private final Duration connectTimeout;
    private final Duration requestTimeout;

    public ProfileSyncConfiguration(Config.Scope config) {
        String envUrl = System.getenv("USER_SERVICE_PROVISION_URL");
        String defaultUrl = (envUrl != null && !envUrl.isBlank())
                ? envUrl
                : "http://user-service:8081/internal/keycloak/users";
        this.userServiceUrl = config != null ? config.get("user-service-url", defaultUrl) : defaultUrl;

        String envSecret = System.getenv("USER_PROFILE_PROVISIONER_SECRET");
        String defaultSecret = (envSecret != null && !envSecret.isBlank())
                ? envSecret
                : "";
        this.sharedSecret = config != null ? config.get("shared-secret", defaultSecret) : defaultSecret;

        this.targetRealm = config != null ? config.get("realm", "ecommerce-realm") : "ecommerce-realm";

        int connectTimeoutMs = config != null ? config.getInt("connect-timeout-ms", 2000) : 2000;
        int requestTimeoutMs = config != null ? config.getInt("request-timeout-ms", 5000) : 5000;

        this.connectTimeout = Duration.ofMillis(connectTimeoutMs);
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
    }

    public ProfileSyncConfiguration(String userServiceUrl, String sharedSecret, String targetRealm,
                                    Duration connectTimeout, Duration requestTimeout) {
        this.userServiceUrl = userServiceUrl;
        this.sharedSecret = sharedSecret;
        this.targetRealm = targetRealm;
        this.connectTimeout = connectTimeout;
        this.requestTimeout = requestTimeout;
    }

    public String getUserServiceUrl() {
        return userServiceUrl;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public String getTargetRealm() {
        return targetRealm;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }
}
