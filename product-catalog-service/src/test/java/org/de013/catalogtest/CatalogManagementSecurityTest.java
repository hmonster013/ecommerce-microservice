package org.de013.catalogtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.de013.productcatalog.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = CatalogManagementSecurityTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.endpoints.web.exposure.include=health,prometheus,info",
        "management.health.defaults.enabled=false",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:1/issuer",
        "spring.cloud.config.enabled=false", "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false", "spring.cloud.compatibility-verifier.enabled=false"
})
class CatalogManagementSecurityTest {
    @Configuration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import(SecurityConfig.class)
    static class App { }

    @DynamicPropertySource
    static void managementPort(DynamicPropertyRegistry registry) throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            int port = socket.getLocalPort();
            registry.add("management.server.port", () -> port);
        }
    }

    @LocalServerPort int serverPort;
    @Value("${management.server.port}") int managementPort;

    @Test
    void onlyManagementPrometheusIsPublic() throws Exception {
        assertThat(get(managementPort, "/actuator/health", false).statusCode()).isEqualTo(200);
        var prometheus = get(managementPort, "/actuator/prometheus", false);
        assertThat(prometheus.statusCode()).isEqualTo(200);
        assertThat(prometheus.body()).contains("# HELP");
        assertThat(get(managementPort, "/actuator/info", false).statusCode()).isIn(401, 403);
        assertThat(get(serverPort, "/actuator/prometheus", false).statusCode()).isIn(401, 403);
        assertThat(get(serverPort, "/actuator/prometheus", true).statusCode()).isIn(401, 403);
    }

    private HttpResponse<String> get(int port, String path, boolean forwarded) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (forwarded) request.header("X-Forwarded-Port", Integer.toString(managementPort))
                .header("Forwarded", "host=localhost:" + managementPort + ";proto=http");
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
