package org.de013.apigateway.config;

import org.de013.apigateway.constant.ApiPaths;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            // User Service Routes
            .route(ApiPaths.USER_SERVICE, r -> r
                .path(ApiPaths.ROUTE_USER_SERVICE)
                .filters(f -> f
                    .rewritePath(ApiPaths.REWRITE_USER_SERVICE, ApiPaths.REMAINING_PATH_REPLACEMENT)
                    .circuitBreaker(c -> c
                        .setName(ApiPaths.CB_USER_SERVICE)
                        .setFallbackUri(ApiPaths.FALLBACK_USER_SERVICE))
                )
                .uri(ApiPaths.LB_USER_SERVICE))
            
            // User Service API Docs
            .route(ApiPaths.USER_SERVICE + "-docs", r -> r
                .path(ApiPaths.DOCS_USER_SERVICE)
                .filters(f -> f.rewritePath(ApiPaths.DOCS_USER_SERVICE, ApiPaths.API_DOCS))
                .uri(ApiPaths.LB_USER_SERVICE))
            
            // Product Catalog Service Routes
            .route(ApiPaths.PRODUCT_CATALOG_SERVICE, r -> r
                .path(ApiPaths.ROUTE_PRODUCT_CATALOG_SERVICE)
                .filters(f -> f
                    .rewritePath(ApiPaths.REWRITE_PRODUCT_CATALOG_SERVICE, ApiPaths.REMAINING_PATH_REPLACEMENT)
                    .circuitBreaker(c -> c
                        .setName(ApiPaths.CB_PRODUCT_SERVICE)
                        .setFallbackUri(ApiPaths.FALLBACK_PRODUCT_CATALOG_SERVICE))
                )
                .uri(ApiPaths.LB_PRODUCT_CATALOG_SERVICE))
            
            // Product Catalog Service API Docs
            .route(ApiPaths.PRODUCT_CATALOG_SERVICE + "-docs", r -> r
                .path(ApiPaths.DOCS_PRODUCT_CATALOG_SERVICE)
                .filters(f -> f.rewritePath(ApiPaths.DOCS_PRODUCT_CATALOG_SERVICE, ApiPaths.API_DOCS))
                .uri(ApiPaths.LB_PRODUCT_CATALOG_SERVICE))
            
            // Shopping Cart Service Routes
            .route(ApiPaths.SHOPPING_CART_SERVICE, r -> r
                .path(ApiPaths.ROUTE_SHOPPING_CART_SERVICE)
                .filters(f -> f
                    .rewritePath(ApiPaths.REWRITE_SHOPPING_CART_SERVICE, ApiPaths.REMAINING_PATH_REPLACEMENT)
                    .circuitBreaker(c -> c
                        .setName(ApiPaths.CB_CART_SERVICE)
                        .setFallbackUri(ApiPaths.FALLBACK_SHOPPING_CART_SERVICE))
                )
                .uri(ApiPaths.LB_SHOPPING_CART_SERVICE))
            
            // Shopping Cart Service API Docs
            .route(ApiPaths.SHOPPING_CART_SERVICE + "-docs", r -> r
                .path(ApiPaths.DOCS_SHOPPING_CART_SERVICE)
                .filters(f -> f.rewritePath(ApiPaths.DOCS_SHOPPING_CART_SERVICE, ApiPaths.API_DOCS))
                .uri(ApiPaths.LB_SHOPPING_CART_SERVICE))
            
            // Order Service Routes
            .route(ApiPaths.ORDER_SERVICE, r -> r
                .path(ApiPaths.ROUTE_ORDER_SERVICE)
                .filters(f -> f
                    .rewritePath(ApiPaths.REWRITE_ORDER_SERVICE, ApiPaths.REMAINING_PATH_REPLACEMENT)
                    .circuitBreaker(c -> c
                        .setName(ApiPaths.CB_ORDER_SERVICE)
                        .setFallbackUri(ApiPaths.FALLBACK_ORDER_SERVICE))
                )
                .uri(ApiPaths.LB_ORDER_SERVICE))
            
            // Order Service API Docs
            .route(ApiPaths.ORDER_SERVICE + "-docs", r -> r
                .path(ApiPaths.DOCS_ORDER_SERVICE)
                .filters(f -> f.rewritePath(ApiPaths.DOCS_ORDER_SERVICE, ApiPaths.API_DOCS))
                .uri(ApiPaths.LB_ORDER_SERVICE))
            
            // Payment Service Routes
            .route(ApiPaths.PAYMENT_SERVICE, r -> r
                .path(ApiPaths.ROUTE_PAYMENT_SERVICE)
                .filters(f -> f
                    .rewritePath(ApiPaths.REWRITE_PAYMENT_SERVICE, ApiPaths.REMAINING_PATH_REPLACEMENT)
                    .circuitBreaker(c -> c
                        .setName(ApiPaths.CB_PAYMENT_SERVICE)
                        .setFallbackUri(ApiPaths.FALLBACK_PAYMENT_SERVICE))
                )
                .uri(ApiPaths.LB_PAYMENT_SERVICE))
            
            // Payment Service API Docs
            .route(ApiPaths.PAYMENT_SERVICE + "-docs", r -> r
                .path(ApiPaths.DOCS_PAYMENT_SERVICE)
                .filters(f -> f.rewritePath(ApiPaths.DOCS_PAYMENT_SERVICE, ApiPaths.API_DOCS))
                .uri(ApiPaths.LB_PAYMENT_SERVICE))
            
            // Notification Service Routes
            .route(ApiPaths.NOTIFICATION_SERVICE, r -> r
                .path(ApiPaths.ROUTE_NOTIFICATION_SERVICE)
                .filters(f -> f
                    .rewritePath(ApiPaths.REWRITE_NOTIFICATION_SERVICE, ApiPaths.REMAINING_PATH_REPLACEMENT)
                    .circuitBreaker(c -> c
                        .setName(ApiPaths.CB_NOTIFICATION_SERVICE)
                        .setFallbackUri(ApiPaths.FALLBACK_NOTIFICATION_SERVICE))
                )
                .uri(ApiPaths.LB_NOTIFICATION_SERVICE))
            
            // Notification Service API Docs
            .route(ApiPaths.NOTIFICATION_SERVICE + "-docs", r -> r
                .path(ApiPaths.DOCS_NOTIFICATION_SERVICE)
                .filters(f -> f.rewritePath(ApiPaths.DOCS_NOTIFICATION_SERVICE, ApiPaths.API_DOCS))
                .uri(ApiPaths.LB_NOTIFICATION_SERVICE))
            
            .build();
    }
}
