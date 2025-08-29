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

        // Version 1 APIs
        groups.add(GroupedOpenApi.builder()
                .pathsToMatch("/api/v1/usersv/**")
                .group("user-service-v1")
                .displayName("User Service (v1)")
                .build());

        groups.add(GroupedOpenApi.builder()
                .pathsToMatch("/api/v1/productsv/**")
                .group("product-catalog-service-v1")
                .displayName("Product Catalog Service (v1)")
                .build());

        groups.add(GroupedOpenApi.builder()
                .pathsToMatch("/api/v1/cartsv/**")
                .group("shopping-cart-service-v1")
                .displayName("Shopping Cart Service (v1)")
                .build());

        groups.add(GroupedOpenApi.builder()
                .pathsToMatch("/api/v1/ordersv/**")
                .group("order-service-v1")
                .displayName("Order Service (v1)")
                .build());

        groups.add(GroupedOpenApi.builder()
                .pathsToMatch("/api/v1/paymentsv/**")
                .group("payment-service-v1")
                .displayName("Payment Service (v1)")
                .build());

        groups.add(GroupedOpenApi.builder()
                .pathsToMatch("/api/v1/notificationsv/**")
                .group("notification-service-v1")
                .displayName("Notification Service (v1)")
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
                .addServersItem(new Server()
                        .url("http://localhost:8080")
                        .description("API Gateway Server"));
    }
}
