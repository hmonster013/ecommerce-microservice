package org.de013.orderservice.config;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.openfeign.CircuitBreakerNameResolver;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Configuration
public class FeignResilienceConfig {
    @Bean
    CircuitBreakerNameResolver feignCircuitBreakerNames() {
        return (name, target, method) -> target.type().getSimpleName() + method.getName();
    }

    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> orderCircuitBreakers() {
        return factory -> factory.configureDefault(id -> {
            var builder = CircuitBreakerConfig.custom().slidingWindowSize(10).minimumNumberOfCalls(5)
                    .failureRateThreshold(50).waitDurationInOpenState(Duration.ofSeconds(10))
                    .permittedNumberOfCallsInHalfOpenState(2);
            switch (id) {
                case "InventoryReservationClientlookup", "PaymentReconciliationClientread" ->
                        builder.ignoreExceptions(FeignException.NotFound.class);
                case "InventoryReservationClientreserve" -> builder.ignoreExceptions(FeignException.Conflict.class);
                case "InventoryReservationClientrelease", "InventoryReservationClientfulfill" ->
                        builder.ignoreExceptions(FeignException.NotFound.class, FeignException.Conflict.class);
                default -> { }
            }
            return new Resilience4JConfigBuilder(id).circuitBreakerConfig(builder.build()).build();
        });
    }

    @Component
    public static class RethrowFallbackFactory implements FallbackFactory<Object> {
        @Override
        public Object create(Throwable cause) {
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException(cause);
        }
    }
}
