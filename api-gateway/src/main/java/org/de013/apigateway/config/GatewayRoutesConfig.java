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
                        .path("/api/user-service/**")
                        .filters(f -> f
                                .rewritePath("/api/user-service/(?<remaining>.*)", "/${remaining}")
                                .circuitBreaker(c -> c
                                        .setName("userServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/user-service"))
                        )
                        .uri("lb://user-service"))

                // User Service API Docs
                .route("user-service-docs", r -> r
                        .path("/api/user-service/v3/api-docs")
                        .filters(f -> f.rewritePath("/api/user-service/v3/api-docs", "/v3/api-docs"))
                        .uri("lb://user-service"))

                // Product Catalog Service Routes
                .route("product-catalog-service", r -> r
                        .path("/api/product-catalog-service/**")
                        .filters(f -> f
                                .rewritePath("/api/product-catalog-service/(?<remaining>.*)", "/${remaining}")
                                .circuitBreaker(c -> c
                                        .setName("productServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/product-catalog-service"))
                        )
                        .uri("lb://product-catalog-service"))

                // Product Catalog Service API Docs
                .route("product-catalog-service-docs", r -> r
                        .path("/api/product-catalog-service/v3/api-docs")
                        .filters(f -> f.rewritePath("/api/product-catalog-service/v3/api-docs", "/v3/api-docs"))
                        .uri("lb://product-catalog-service"))

                // Shopping Cart Service Routes
                .route("shopping-cart-service", r -> r
                        .path("/api/shopping-cart-service/**")
                        .filters(f -> f
                                .rewritePath("/api/shopping-cart-service/(?<remaining>.*)", "/${remaining}")
                                .circuitBreaker(c -> c
                                        .setName("cartServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/shopping-cart-service"))
                        )
                        .uri("lb://shopping-cart-service"))

                // Shopping Cart Service API Docs
                .route("shopping-cart-service-docs", r -> r
                        .path("/api/shopping-cart-service/v3/api-docs")
                        .filters(f -> f.rewritePath("/api/shopping-cart-service/v3/api-docs", "/v3/api-docs"))
                        .uri("lb://shopping-cart-service"))

                // Order Service Routes
                .route("order-service", r -> r
                        .path("/api/order-service/**")
                        .filters(f -> f
                                .rewritePath("/api/order-service/(?<remaining>.*)", "/${remaining}")
                                .circuitBreaker(c -> c
                                        .setName("orderServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/order-service"))
                        )
                        .uri("lb://order-service"))

                // Order Service API Docs
                .route("order-service-docs", r -> r
                        .path("/api/order-service/v3/api-docs")
                        .filters(f -> f.rewritePath("/api/order-service/v3/api-docs", "/v3/api-docs"))
                        .uri("lb://order-service"))

                // Payment Service Routes
                .route("payment-service", r -> r
                        .path("/api/payment-service/**")
                        .filters(f -> f
                                .rewritePath("/api/payment-service/(?<remaining>.*)", "/${remaining}")
                                .circuitBreaker(c -> c
                                        .setName("paymentServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/payment-service"))
                        )
                        .uri("lb://payment-service"))

                // Payment Service API Docs
                .route("payment-service-docs", r -> r
                        .path("/api/payment-service/v3/api-docs")
                        .filters(f -> f.rewritePath("/api/payment-service/v3/api-docs", "/v3/api-docs"))
                        .uri("lb://payment-service"))

                // Notification Service Routes
                .route("notification-service", r -> r
                        .path("/api/notification-service/**")
                        .filters(f -> f
                                .rewritePath("/api/notification-service/(?<remaining>.*)", "/${remaining}")
                                .circuitBreaker(c -> c
                                        .setName("notificationServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/notification-service"))
                        )
                        .uri("lb://notification-service"))

                // Notification Service API Docs
                .route("notification-service-docs", r -> r
                        .path("/api/notification-service/v3/api-docs")
                        .filters(f -> f.rewritePath("/api/notification-service/v3/api-docs", "/v3/api-docs"))
                        .uri("lb://notification-service"))

                .build();

        return routeLocator;
    }
}
