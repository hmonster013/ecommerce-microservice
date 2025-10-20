package org.de013.userservice.config;

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
 * OpenAPI 3 configuration for User Service
 * Configures Swagger UI with security schemes and API documentation
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8081}")
    private String serverPort;

    @Value("${spring.application.name:user-service}")
    private String applicationName;

    @Bean
    public OpenAPI userServiceOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(serverList())
                .addSecurityItem(securityRequirement())
                .components(securityComponents());
    }

    private Info apiInfo() {
        return new Info()
                .title("User Service API")
                .description("""
                    **User Service** provides comprehensive user management and authentication capabilities for the e-commerce platform.
                    
                    ## Features
                    - User registration and authentication
                    - JWT token-based security
                    - Profile management
                    - Role-based access control
                    - Password management
                    - Email validation
                    
                    ## Authentication
                    This API uses JWT (JSON Web Token) for authentication. To access protected endpoints:
                    1. Register a new user or login with existing credentials
                    2. Use the returned JWT token in the Authorization header
                    3. Format: `Authorization: Bearer <your-jwt-token>`
                    
                    ## Rate Limiting
                    API endpoints are rate-limited to prevent abuse:
                    - Authentication endpoints: 5 requests per minute
                    - General endpoints: 100 requests per minute
                    
                    ## Error Handling
                    All errors follow a consistent format with appropriate HTTP status codes and detailed error messages.
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
                        .url("http://localhost:8080/api/user-service")
                        .description("Via API Gateway (Local)"),
                new Server()
                        .url("http://api-gateway:8080/api/user-service")
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
