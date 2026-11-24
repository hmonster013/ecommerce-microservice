package org.de013.userservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.keycloak-profile")
@Getter
@Setter
public class KeycloakProfileProperties {
    private String baseUrl;
    private String realm;
    private String clientId;
    private String clientSecret;
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration requestTimeout = Duration.ofSeconds(5);
}
