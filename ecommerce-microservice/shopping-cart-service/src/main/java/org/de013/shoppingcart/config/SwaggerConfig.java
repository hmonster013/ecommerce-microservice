package org.de013.shoppingcart.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Comprehensive Swagger Configuration for API Documentation
 * Provides detailed API documentation with security, examples, and error codes
 */
@Configuration
public class SwaggerConfig {

    @Value("${server.port:8083}")
    private String serverPort;

    @Value("${spring.application.name:shopping-cart-service}")
    private String applicationName;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(apiServers())
                .security(List.of(new SecurityRequirement().addList("bearerAuth")))
                .components(apiComponents())
                .externalDocs(externalDocumentation())
                .tags(apiTags());
    }

    private Info apiInfo() {
        return new Info()
                .title("Shopping Cart Service API")
                .version("1.0.0")
                .description("""
                    # Shopping Cart Service API
                    
                    A comprehensive microservice for managing shopping carts in an e-commerce platform.
                    
                    ## Features
                    - **Cart Management**: Create, update, delete shopping carts
                    - **Item Management**: Add, remove, update cart items
                    - **User & Guest Support**: Support for both authenticated users and guest sessions
                    - **Real-time Pricing**: Dynamic pricing with discounts and promotions
                    - **Multi-layer Caching**: High-performance caching with Redis and Caffeine
                    - **Analytics**: Comprehensive cart analytics and tracking
                    - **Security**: JWT-based authentication with role-based access control
                    - **Performance Monitoring**: Real-time performance metrics and health checks
                    
                    ## Authentication
                    Most endpoints require JWT authentication. Guest endpoints are available for anonymous users.
                    
                    ## Rate Limiting
                    API calls are rate-limited to ensure fair usage and system stability.
                    
                    ## Error Handling
                    All endpoints return standardized error responses with appropriate HTTP status codes.
                    """)
                .termsOfService("https://example.com/terms")
                .contact(new Contact()
                        .name("Shopping Cart Service Team")
                        .email("shopping-cart-dev@example.com")
                        .url("https://github.com/example/shopping-cart-service"))
                .license(new License()
                        .name("MIT License")
                        .url("https://opensource.org/licenses/MIT"));
    }

    private List<Server> apiServers() {
        return List.of(
                new Server()
                        .url("http://localhost:" + serverPort)
                        .description("Local Development Server"),
                new Server()
                        .url("https://api-dev.example.com")
                        .description("Development Environment"),
                new Server()
                        .url("https://api-staging.example.com")
                        .description("Staging Environment"),
                new Server()
                        .url("https://api.example.com")
                        .description("Production Environment")
        );
    }

    private Components apiComponents() {
        return new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT token obtained from authentication service"));
    }

    private ExternalDocumentation externalDocumentation() {
        return new ExternalDocumentation()
                .description("Shopping Cart Service Documentation")
                .url("https://docs.example.com/shopping-cart-service");
    }

    private List<Tag> apiTags() {
        return List.of(
                new Tag()
                        .name("Cart Management")
                        .description("Operations for managing shopping carts"),
                new Tag()
                        .name("Cart Items")
                        .description("Operations for managing items within carts"),
                new Tag()
                        .name("Guest Operations")
                        .description("Operations available for guest users"),
                new Tag()
                        .name("Analytics")
                        .description("Cart analytics and reporting endpoints"),
                new Tag()
                        .name("Business Logic")
                        .description("Business logic operations (pricing, validation, etc.)"),
                new Tag()
                        .name("External Services")
                        .description("Integration with external services"),
                new Tag()
                        .name("Performance")
                        .description("Performance monitoring and optimization"),
                new Tag()
                        .name("Health & Monitoring")
                        .description("Health checks and system monitoring")
        );
    }
}
