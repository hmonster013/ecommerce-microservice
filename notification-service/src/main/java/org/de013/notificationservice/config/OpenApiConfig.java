package org.de013.notificationservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
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
                .components(securityComponents());
    }

    private Info apiInfo() {
        return new Info()
                .title("Simple Notification Service API")
                .description("""
                    **Notification Service** provides simple email and SMS notification capabilities for the e-commerce platform.

                    ## Features
                    - Email notifications via SMTP
                    - SMS notifications via Twilio (with mock mode)
                    - Notification status tracking
                    - User notification history
                    - Read/unread status management

                    ## Available Endpoints
                    - **POST /send-email** - Send email notification
                    - **POST /send-sms** - Send SMS notification
                    - **POST /send-both** - Send both email and SMS
                    - **GET /{id}** - Get notification by ID
                    - **GET /user/{userId}** - Get user notifications
                    - **PUT /{id}/read** - Mark notification as read
                    - **GET /user/{userId}/unread-count** - Get unread count

                    ## Authentication
                    This API uses JWT (JSON Web Token) for authentication. To access protected endpoints:
                    1. Obtain JWT token from user-service
                    2. Use the returned JWT token in the Authorization header
                    3. Format: `Authorization: Bearer <your-jwt-token>`

                    ## Configuration
                    Configure email and SMS settings in application.yml
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
                        .url("http://localhost:8080/api/notification-service")
                        .description("Via API Gateway (Local)"),
                new Server()
                        .url("http://api-gateway:8080/api/notification-service")
                        .description("Via API Gateway (Docker)")
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
