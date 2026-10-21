package org.de013.paymentservice.client;

import feign.Feign;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.de013.paymentservice.dto.external.OrderDto;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Owned hosted checkout must preserve 403/404, never replace them with fallback order data. */
@FeignClient(name = "order-service", contextId = "hostedCheckoutOrders", path = "/orders",
        configuration = HostedOrderClient.Configuration.class)
public interface HostedOrderClient {
    @GetMapping("/{orderId}")
    ResponseEntity<OrderDto> read(@PathVariable("orderId") Long orderId,
            @RequestHeader("X-User-Id") String subject, @RequestHeader("X-User-Username") String username);

    @PutMapping("/{orderId}/mark-paid")
    ResponseEntity<Void> markPaid(@PathVariable("orderId") Long orderId,
            @RequestParam("paymentId") Long paymentId, @RequestParam("paymentNumber") String paymentNumber,
            @RequestHeader("X-User-Id") String subject, @RequestHeader("X-User-Username") String username);

    // Client-scoped only: intentionally not component-scanned as global Feign configuration.
    class Configuration {
        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        public Feign.Builder hostedFeignBuilder() {
            return Feign.builder().retryer(Retryer.NEVER_RETRY);
        }

        @Bean
        public ErrorDecoder hostedOrderErrors() {
            return (method, response) -> {
                if (response.status() == 403) return new AccessDeniedException("Order access denied");
                if (response.status() == 404) return new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
                return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to verify order");
            };
        }
    }
}
