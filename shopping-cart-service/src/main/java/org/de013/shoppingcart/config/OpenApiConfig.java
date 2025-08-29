package org.de013.shoppingcart.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI Configuration for Shopping Cart Service
 * Provides comprehensive API documentation with Swagger UI
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8083}")
    private String serverPort;

    @Value("${spring.application.name:shopping-cart-service}")
    private String applicationName;

    @Bean
    public OpenAPI shoppingCartOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Shopping Cart Service API")
                        .description("""
                                **Shopping Cart Service** - A high-performance microservice for managing shopping carts in an e-commerce platform.
                                
                                ## Features
                                - **Redis-based Storage**: Ultra-fast cart operations with Redis primary storage
                                - **Session Management**: Support for both authenticated users and guest sessions
                                - **Real-time Validation**: Product availability and pricing validation
                                - **Cart Persistence**: Automatic cart saving and restoration
                                - **Bulk Operations**: Efficient bulk add/remove operations
                                - **Price Calculation**: Dynamic pricing with discounts and tax calculation
                                
                                ## Architecture
                                - **Primary Storage**: Redis for active cart operations
                                - **Backup Storage**: PostgreSQL for cart history and analytics
                                - **Service Integration**: Feign clients for Product Catalog and User services
                                - **Caching Strategy**: Multi-layer caching with configurable TTL
                                
                                ## Performance
                                - **Response Time**: < 100ms for cart operations
                                - **Throughput**: 1000+ requests/second
                                - **Availability**: 99.9% uptime target
                                - **Cache Hit Rate**: 95%+ for active carts
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("E-commerce Development Team")
                                .email("dev@ecommerce.com")
                                .url("https://github.com/de013/ecommerce-microservice"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Direct Service Access (Development)"),
                        new Server()
                                .url("http://localhost:8080/api/v1/cartsv")
                                .description("API Gateway (Development)")
                ))
                .tags(List.of(
                        new Tag()
                                .name("Cart Management")
                                .description("Core shopping cart operations - create, read, update, delete carts"),
                        new Tag()
                                .name("Cart Items")
                                .description("Cart item management - add, update, remove items from cart"),
                        new Tag()
                                .name("Cart Validation")
                                .description("Cart validation operations - validate products, prices, and availability"),
                        new Tag()
                                .name("Cart Operations")
                                .description("Advanced cart operations - merge, checkout preparation, bulk operations"),
                        new Tag()
                                .name("Session Management")
                                .description("Guest session and cart transfer operations"),
                        new Tag()
                                .name("Health & Monitoring")
                                .description("Service health checks and monitoring endpoints")
                ))
                .addSecurityItem(securityRequirement())
                .components(securityComponents());
    }

    private SecurityRequirement securityRequirement() {
        return new SecurityRequirement()
                .addList("Bearer Authentication");
    }

    private Components securityComponents() {
        return new Components()
                .addSecuritySchemes("Bearer Authentication", securityScheme());
    }

    private SecurityScheme securityScheme() {
        return new SecurityScheme()
                .name("Bearer Authentication")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("""
                    JWT Authorization header using the Bearer scheme.

                    Enter 'Bearer' [space] and then your token in the text input below.

                    Example: "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
                    """);
    }
}
