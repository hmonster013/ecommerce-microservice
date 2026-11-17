package org.de013.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;

@AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.endpoints.web.exposure.include=health,prometheus,info",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:1/jwks",
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.compatibility-verifier.enabled=false"
})
class ManagementSecurityTest {
    @DynamicPropertySource
    static void managementPort(DynamicPropertyRegistry registry) throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            int port = socket.getLocalPort();
            registry.add("management.server.port", () -> port);
        }
    }

    @LocalServerPort
    int serverPort;

    @Value("${management.server.port}")
    int managementPort;

    @Test
    void onlyManagementHealthAndPrometheusAreAnonymous() {
        WebTestClient management = client(managementPort);
        management.get().uri("/actuator/health").exchange().expectStatus().isOk();
        management.get().uri("/actuator/prometheus").exchange().expectStatus().isOk()
                .expectBody(String.class).value(body -> org.assertj.core.api.Assertions.assertThat(body).contains("# HELP"));
        management.get().uri("/actuator/info").exchange().expectStatus().isUnauthorized();

        WebTestClient main = client(serverPort);
        main.get().uri("/actuator/prometheus").exchange().expectStatus().isUnauthorized();
        main.get().uri("/actuator/prometheus")
                .header("X-Forwarded-Port", Integer.toString(managementPort))
                .header("Forwarded", "host=localhost:" + managementPort + ";proto=http")
                .exchange().expectStatus().isUnauthorized();
        main.get().uri("/api/v1/user-service/users/profile")
                .header("X-User-Id", "spoofed").header("X-User-Email", "spoofed@example.com")
                .header("X-User-Roles", "ADMIN").exchange().expectStatus().isUnauthorized();
    }

    private WebTestClient client(int port) {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
