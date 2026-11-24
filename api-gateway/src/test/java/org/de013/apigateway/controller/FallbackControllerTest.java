package org.de013.apigateway.controller;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

class FallbackControllerTest {
    private final WebTestClient client = WebTestClient.bindToController(new FallbackController()).build();

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"})
    void returnsServiceUnavailableForOriginalMethod(String method) {
        client.method(HttpMethod.valueOf(method)).uri("/fallback/catalog").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.method").isEqualTo(method);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HEAD"})
    void headReturnsServiceUnavailableWithoutBody(String method) {
        client.method(HttpMethod.valueOf(method)).uri("/fallback/catalog").exchange()
                .expectStatus().isEqualTo(503).expectBody().isEmpty();
    }
}
