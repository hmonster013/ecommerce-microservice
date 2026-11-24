package org.de013.paymentservice.service;

import feign.Request;
import feign.Response;
import org.de013.paymentservice.client.HostedOrderClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class HostedOrderClientTest {
    @Test void realFeignInvocationPropagates403WithoutFallbackOrRetry() {
        var config = new HostedOrderClient.Configuration();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var client = config.hostedFeignBuilder()
                .contract(new org.springframework.cloud.openfeign.support.SpringMvcContract())
                .errorDecoder(config.hostedOrderErrors())
                .client((request, options) -> {
                    calls.incrementAndGet();
                    return Response.builder().request(request).status(403).reason("Forbidden").build();
                }).target(HostedOrderClient.class, "http://fixture.test");
        assertThrows(AccessDeniedException.class, () -> client.read(1L, "other", "hosted-checkout"));
        assertEquals(1, calls.get());
    }

    @Test void keepsForbiddenAndNotFoundInsteadOfFabricatedFallbackOrder() {
        var config = new HostedOrderClient.Configuration();
        var request = Request.create(Request.HttpMethod.GET, "http://fixture.test/orders/1", Map.of(), null, StandardCharsets.UTF_8, null);
        assertInstanceOf(AccessDeniedException.class, config.hostedOrderErrors().decode("read",
                Response.builder().request(request).status(403).reason("Forbidden").build()));
        var missing = config.hostedOrderErrors().decode("read", Response.builder().request(request).status(404).reason("Not Found").build());
        assertEquals(HttpStatus.NOT_FOUND, assertInstanceOf(ResponseStatusException.class, missing).getStatusCode());
        var unavailable = config.hostedOrderErrors().decode("read", Response.builder().request(request).status(503).reason("Unavailable").build());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertInstanceOf(ResponseStatusException.class, unavailable).getStatusCode());
        assertEquals(feign.Feign.Builder.class, config.hostedFeignBuilder().getClass());
    }
}
