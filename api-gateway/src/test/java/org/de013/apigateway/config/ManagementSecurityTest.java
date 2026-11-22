package org.de013.apigateway.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMetrics
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

    @Autowired ServerCodecConfigurer codecs;
    @Autowired ObjectMapper objectMapper;

    @Test
    @SuppressWarnings("removal")
    void webFluxUsesGatewayJackson2MapperForJson() {
        var writer = codecs.getWriters().stream()
                .filter(EncoderHttpMessageWriter.class::isInstance)
                .map(EncoderHttpMessageWriter.class::cast)
                .map(EncoderHttpMessageWriter::getEncoder)
                .filter(Jackson2JsonEncoder.class::isInstance)
                .map(Jackson2JsonEncoder.class::cast).findFirst().orElseThrow();
        var decoder = codecs.getReaders().stream()
                .filter(DecoderHttpMessageReader.class::isInstance)
                .map(DecoderHttpMessageReader.class::cast)
                .map(DecoderHttpMessageReader::getDecoder)
                .filter(Jackson2JsonDecoder.class::isInstance)
                .map(Jackson2JsonDecoder.class::cast).findFirst().orElseThrow();
        assertThat(writer.getObjectMapper()).isSameAs(objectMapper);
        assertThat(decoder.getObjectMapper()).isSameAs(objectMapper);
        var type = ResolvableType.forClass(JsonFixture.class);
        assertThat(codecs.getWriters().stream().filter(candidate -> candidate.canWrite(type, MediaType.APPLICATION_JSON))
                .findFirst().orElseThrow()).isInstanceOfSatisfying(EncoderHttpMessageWriter.class,
                        selected -> assertThat(selected.getEncoder()).isSameAs(writer));
        assertThat(codecs.getReaders().stream().filter(candidate -> candidate.canRead(type, MediaType.APPLICATION_JSON))
                .findFirst().orElseThrow()).isInstanceOfSatisfying(DecoderHttpMessageReader.class,
                        selected -> assertThat(selected.getDecoder()).isSameAs(decoder));

        var buffer = writer.encodeValue(new JsonFixture(LocalDate.of(2024, 1, 2), new BigDecimal("12.30"), null),
                DefaultDataBufferFactory.sharedInstance, type,
                MediaType.APPLICATION_JSON, Map.of());
        try {
            assertThat(buffer.toString(StandardCharsets.UTF_8))
                    .isEqualTo("{\"date\":[2024,1,2],\"amount\":12.30}");
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record JsonFixture(LocalDate date, BigDecimal amount, String optional) { }

    @Test
    void configuredOpenApiEndpointServesSchema() {
        client(serverPort).get().uri("/v1/api-docs").exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().jsonPath("$.openapi").isNotEmpty()
                .jsonPath("$.info.title").isNotEmpty()
                .jsonPath("$.info.version").isNotEmpty();
    }

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
