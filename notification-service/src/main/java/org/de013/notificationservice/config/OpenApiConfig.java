package org.de013.notificationservice.config;

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
 * OpenAPI 3 configuration for Notification Service
 * Configures Swagger UI with security schemes and API documentation
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8086}")
    private String serverPort;

    @Value("${spring.application.name:notification-service}")
    private String applicationName;

    @Bean
    public OpenAPI notificationServiceOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(serverList())
                .addSecurityItem(securityRequirement())
                .components(securityComponents())
                .tags(tagList());
    }

    private Info apiInfo() {
        return new Info()
                .title("Notification Service API")
                .description("""
                    **Notification Service** provides comprehensive notification management for the e-commerce platform.
                    
                    ## Features
                    - Email notifications
                    - SMS notifications (future)
                    - Push notifications (future)
                    - Notification templates
                    - Notification history and tracking
                    - Event-driven notifications
                    
                    ## Authentication
                    This API uses JWT (JSON Web Token) for authentication. To access protected endpoints:
                    1. Obtain a JWT token from the user service
                    2. Use the returned JWT token in the Authorization header
                    3. Format: `Authorization: Bearer <your-jwt-token>`
                    
                    ## Notification Types
                    - Welcome emails
                    - Order confirmations
                    - Payment confirmations
                    - Shipping updates
                    - Password reset
                    - Account verification
                    
                    ## Event Integration
                    The service listens to events from other microservices via Kafka and automatically
                    sends appropriate notifications based on configured templates.
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
                        .url("http://localhost:8080/api/v1/notificationsv")
                        .description("API Gateway (Development)")
        );
    }

    private List<Tag> tagList() {
        return List.of(
                new Tag()
                        .name("Notifications")
                        .description("Notification management operations - send, track, manage notifications"),
                new Tag()
                        .name("Email")
                        .description("Email notification operations"),
                new Tag()
                        .name("Templates")
                        .description("Notification template management"),
                new Tag()
                        .name("History")
                        .description("Notification history and tracking operations"),
                new Tag()
                        .name("Events")
                        .description("Event-driven notification operations")
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
