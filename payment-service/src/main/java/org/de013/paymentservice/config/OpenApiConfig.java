package org.de013.paymentservice.config;

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
 * OpenAPI 3 configuration for Payment Service
 * Configures Swagger UI with security schemes and API documentation
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8085}")
    private String serverPort;

    @Value("${spring.application.name:payment-service}")
    private String applicationName;

    @Bean
    public OpenAPI paymentServiceOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(serverList())
                .addSecurityItem(securityRequirement())
                .components(securityComponents())
                .tags(tagList());
    }

    private Info apiInfo() {
        return new Info()
                .title("Payment Service API")
                .description("""
                    **Payment Service** provides secure payment processing capabilities for the e-commerce platform.
                    
                    ## Features
                    - Payment processing and validation
                    - Multiple payment methods support
                    - Payment status tracking
                    - Refund and cancellation handling
                    - Payment history and analytics
                    - Secure transaction management
                    
                    ## Authentication
                    This API uses JWT (JSON Web Token) for authentication. To access protected endpoints:
                    1. Obtain a JWT token from the user service
                    2. Use the returned JWT token in the Authorization header
                    3. Format: `Authorization: Bearer <your-jwt-token>`
                    
                    ## Payment Flow
                    1. Create payment intent
                    2. Process payment with provider
                    3. Validate payment result
                    4. Update order status
                    5. Send confirmation
                    
                    ## Security
                    All payment data is encrypted and processed securely following PCI DSS standards.
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
                        .url("http://localhost:8080/api/v1/paymentsv")
                        .description("API Gateway (Development)")
        );
    }

    private List<Tag> tagList() {
        return List.of(
                new Tag()
                        .name("Payments")
                        .description("Payment processing operations - create, process, validate payments"),
                new Tag()
                        .name("Payment Methods")
                        .description("Payment method management operations"),
                new Tag()
                        .name("Payment Status")
                        .description("Payment status tracking and updates"),
                new Tag()
                        .name("Refunds")
                        .description("Refund and cancellation operations"),
                new Tag()
                        .name("Payment History")
                        .description("Payment history and analytics operations")
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
