package org.de013.apigateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public List<GroupedOpenApi> apis() {
        List<GroupedOpenApi> groups = new ArrayList<>();

        // Service APIs using Eureka discovery pattern
        groups.add(GroupedOpenApi.builder()
                .pathsToMatch("/api/user-service/**")
                .group("user-service")
                .displayName("User Service")
                .build());

        groups.add(GroupedOpenApi.builder()
                .pathsToMatch("/api/product-catalog-service/**")
                .group("product-catalog-service")
                .displayName("Product Catalog Service")
                .build());

        groups.add(GroupedOpenApi.builder()
                .pathsToMatch("/api/shopping-cart-service/**")
                .group("shopping-cart-service")
                .displayName("Shopping Cart Service")
                .build());

        groups.add(GroupedOpenApi.builder()
                .pathsToMatch("/api/order-service/**")
                .group("order-service")
                .displayName("Order Service")
                .build());

        groups.add(GroupedOpenApi.builder()
                .pathsToMatch("/api/payment-service/**")
                .group("payment-service")
                .displayName("Payment Service")
                .build());

        groups.add(GroupedOpenApi.builder()
                .pathsToMatch("/api/notification-service/**")
                .group("notification-service")
                .displayName("Notification Service")
                .build());

        return groups;
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("E-commerce Microservices API")
                        .description("API Gateway for E-commerce Platform")
                        .version("1.0.0"))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("API Gateway (Local)"),
                        new Server()
                                .url("http://api-gateway:8080")
                                .description("API Gateway (Docker)")));
    }
}
