package org.de013.apigateway.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public List<GroupedOpenApi> apis() {
        List<GroupedOpenApi> groups = new ArrayList<>();

        // Static configuration matching the gateway routes
        groups.add(GroupedOpenApi.builder()
                .pathsToMatch("/api/users/**")
                .group("user-service")
                .build());

        groups.add(GroupedOpenApi.builder()
                .pathsToMatch("/api/products/**")
                .group("product-catalog-service")
                .build());

        groups.add(GroupedOpenApi.builder()
                .pathsToMatch("/api/cart/**")
                .group("shopping-cart-service")
                .build());

        groups.add(GroupedOpenApi.builder()
                .pathsToMatch("/api/orders/**")
                .group("order-service")
                .build());

        groups.add(GroupedOpenApi.builder()
                .pathsToMatch("/api/payments/**")
                .group("payment-service")
                .build());

        groups.add(GroupedOpenApi.builder()
                .pathsToMatch("/api/notifications/**")
                .group("notification-service")
                .build());

        return groups;
    }
}
