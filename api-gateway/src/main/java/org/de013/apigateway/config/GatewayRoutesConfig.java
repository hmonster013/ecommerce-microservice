package org.de013.apigateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfig {

    private static final Logger logger = LoggerFactory.getLogger(GatewayRoutesConfig.class);

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        RouteLocator routeLocator = builder.routes()
                // User Service Routes
                .route("user-service", r -> r
                        .path("/api/v1/user-service/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/user-service/(?<remaining>.*)", "/${remaining}")
                                .circuitBreaker(c -> c
                                        .setName("userServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/user-service"))
                        )
                        .uri("lb://user-service"))

                // User Service API Docs
                .route("user-service-docs", r -> r
                        .path("/api/v1/user-service/v1/api-docs")
                        .filters(f -> f.rewritePath("/api/v1/user-service/v1/api-docs", "/v1/api-docs"))
                        .uri("lb://user-service"))

                // Product Catalog Service Routes
                .route("product-catalog-service", r -> r
                        .path("/api/v1/product-catalog-service/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/product-catalog-service/(?<remaining>.*)", "/${remaining}")
                                .circuitBreaker(c -> c
                                        .setName("productServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/product-catalog-service"))
                        )
                        .uri("lb://product-catalog-service"))

                // Product Catalog Service API Docs
                .route("product-catalog-service-docs", r -> r
                        .path("/api/v1/product-catalog-service/v1/api-docs")
                        .filters(f -> f.rewritePath("/api/v1/product-catalog-service/v1/api-docs", "/v1/api-docs"))
                        .uri("lb://product-catalog-service"))

                // Shopping Cart Service Routes
                .route("shopping-cart-service", r -> r
                        .path("/api/v1/shopping-cart-service/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/shopping-cart-service/(?<remaining>.*)", "/${remaining}")
                                .circuitBreaker(c -> c
                                        .setName("cartServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/shopping-cart-service"))
                        )
                        .uri("lb://shopping-cart-service"))

                // Shopping Cart Service API Docs
                .route("shopping-cart-service-docs", r -> r
                        .path("/api/v1/shopping-cart-service/v1/api-docs")
                        .filters(f -> f.rewritePath("/api/v1/shopping-cart-service/v1/api-docs", "/v1/api-docs"))
                        .uri("lb://shopping-cart-service"))

                // Order Service Routes
                .route("order-service", r -> r
                        .path("/api/v1/order-service/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/order-service/(?<remaining>.*)", "/${remaining}")
                                .circuitBreaker(c -> c
                                        .setName("orderServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/order-service"))
                        )
                        .uri("lb://order-service"))

                // Order Service API Docs
                .route("order-service-docs", r -> r
                        .path("/api/v1/order-service/v1/api-docs")
                        .filters(f -> f.rewritePath("/api/v1/order-service/v1/api-docs", "/v1/api-docs"))
                        .uri("lb://order-service"))

                // Payment Service Routes
                .route("payment-service", r -> r
                        .path("/api/v1/payment-service/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/payment-service/(?<remaining>.*)", "/${remaining}")
                                .circuitBreaker(c -> c
                                        .setName("paymentServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/payment-service"))
                        )
                        .uri("lb://payment-service"))

                // Payment Service API Docs
                .route("payment-service-docs", r -> r
                        .path("/api/v1/payment-service/v1/api-docs")
                        .filters(f -> f.rewritePath("/api/v1/payment-service/v1/api-docs", "/v1/api-docs"))
                        .uri("lb://payment-service"))

                // Notification Service Routes
                .route("notification-service", r -> r
                        .path("/api/v1/notification-service/**")
                        .filters(f -> f
                                .rewritePath("/api/v1/notification-service/(?<remaining>.*)", "/${remaining}")
                                .circuitBreaker(c -> c
                                        .setName("notificationServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/notification-service"))
                        )
                        .uri("lb://notification-service"))

                // Notification Service API Docs
                .route("notification-service-docs", r -> r
                        .path("/api/v1/notification-service/v1/api-docs")
                        .filters(f -> f.rewritePath("/api/v1/notification-service/v1/api-docs", "/v1/api-docs"))
                        .uri("lb://notification-service"))

                // API Gateway's Own Docs Route (Forwarding /api/v1/v1/api-docs to internal /v1/api-docs)
                .route("api-gateway-docs", r -> r
                        .path("/api/v1/v1/api-docs/**")
                        .filters(f -> f.rewritePath("/api/v1/v1/api-docs(?<remaining>.*)", "/v1/api-docs${remaining}"))
                        .uri("http://localhost:8080"))

                .build();

        return routeLocator;
    }
}
