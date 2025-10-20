package org.de013.orderservice.config;

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
 * OpenAPI 3 configuration for Order Service
 * Configures Swagger UI with security schemes and API documentation
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8084}")
    private String serverPort;

    @Value("${spring.application.name:order-service}")
    private String applicationName;

    @Bean
    public OpenAPI orderServiceOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(serverList())
                .addSecurityItem(securityRequirement())
                .components(securityComponents())
                .tags(tagList());
    }

    private Info apiInfo() {
        return new Info()
                .title("Order Service API")
                .description("""
                    **Order Service** provides comprehensive order management capabilities for the e-commerce platform.
                    
                    ## Features
                    - Order creation and management
                    - Order status tracking
                    - Payment integration
                    - Inventory management
                    - Order history and analytics
                    - Real-time notifications
                    
                    ## Authentication
                    This API uses JWT (JSON Web Token) for authentication. To access protected endpoints:
                    1. Obtain a JWT token from the user service
                    2. Use the returned JWT token in the Authorization header
                    3. Format: `Authorization: Bearer <your-jwt-token>`
                    
                    ## Order Workflow
                    1. Create order from shopping cart
                    2. Process payment
                    3. Update inventory
                    4. Send notifications
                    5. Track order status
                    """)
                .version("1.0.0")
                .contact(contact())
                .license(license());
    }

    private Contact contact() {
        return new Contact()
                .name("Development Team")
                .email("dev@de013.org")
                .url("https://github.com/de013/ecommerce-microservice");
    }

    private License license() {
        return new License()
                .name("MIT License")
                .url("https://opensource.org/licenses/MIT");
    }

    private List<Server> serverList() {
        return List.of(
                new Server()
                        .url("http://localhost:" + serverPort)
                        .description("Direct Access (Local Development)"),
                new Server()
                        .url("http://localhost:8080/api/order-service")
                        .description("Via API Gateway (Local)"),
                new Server()
                        .url("http://api-gateway:8080/api/order-service")
                        .description("Via API Gateway (Docker)")
        );
    }

    private List<Tag> tagList() {
        return List.of(
        );
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
