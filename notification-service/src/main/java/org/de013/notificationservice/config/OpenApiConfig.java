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
                    **Notification Service** provides enterprise-grade notification and communication capabilities for the e-commerce platform.

                    ## 🚀 Core Features

                    ### Multi-Channel Delivery
                    - ✅ **Email**: SMTP with HTML/plain text support
                    - ✅ **SMS**: Twilio integration with international support
                    - ✅ **Push Notifications**: Firebase FCM for mobile apps
                    - ✅ **In-App**: Real-time in-application notifications

                    ### Advanced Template Engine
                    - ✅ **Dynamic Templates**: Variables, conditionals, loops
                    - ✅ **Template Inheritance**: Parent-child template relationships
                    - ✅ **Rich Content**: HTML, media attachments, styling
                    - ✅ **Real-time Preview**: Template testing and validation

                    ### Localization & Personalization
                    - ✅ **12 Languages**: en, vi, fr, es, de, ja, ko, zh, pt, it, ru, ar
                    - ✅ **Auto-translation**: Intelligent translation fallbacks
                    - ✅ **Personalization**: AI-powered content customization
                    - ✅ **A/B Testing**: Content variation testing

                    ### Enterprise Capabilities
                    - ✅ **GDPR Compliance**: Privacy, consent, opt-out management
                    - ✅ **Rate Limiting**: Intelligent throttling and queue management
                    - ✅ **Analytics**: Comprehensive delivery and engagement metrics
                    - ✅ **Audit Trails**: Complete activity logging

                    ## 🔐 Authentication
                    Multiple authentication methods supported:
                    - **X-User-ID**: User identifier for user-specific operations
                    - **X-Service-ID**: Service identifier for inter-service communication
                    - **Authorization**: Bearer JWT token for secure operations

                    ## 📊 Notification Categories

                    ### Transactional
                    - Order confirmations, shipping updates
                    - Payment confirmations, receipts
                    - Account verification, password reset

                    ### Marketing
                    - Promotional campaigns, newsletters
                    - Product recommendations
                    - Seasonal offers, flash sales

                    ### System
                    - Welcome messages, onboarding
                    - Security alerts, account changes
                    - System maintenance, updates

                    ## 🔄 Event-Driven Architecture
                    Real-time integration with microservices via RabbitMQ:
                    - **Order Events**: Automatic order lifecycle notifications
                    - **User Events**: Registration, profile updates
                    - **Payment Events**: Payment status notifications
                    - **Engagement Events**: Click tracking, unsubscribe handling
                    """)
                .version("1.0.0")
                .contact(contact())
                .license(license());
    }

    private Contact contact() {
        return new Contact()
                .name("Notification Service Team")
                .email("notifications@de013.org")
                .url("https://github.com/de013/ecommerce-microservice/tree/main/notification-service");
    }

    private License license() {
        return new License()
                .name("MIT License")
                .url("https://opensource.org/licenses/MIT");
    }

    private List<Server> serverList() {
        return List.of(
                new Server()
                        .url("http://localhost:8080/api/v1/notifications")
                        .description("API Gateway (Development)"),
                new Server()
                        .url("http://localhost:" + serverPort)
                        .description("Direct Service Access (Development)"),
                new Server()
                        .url("https://api.de013.org/api/v1/notifications")
                        .description("Production Environment")
        );
    }

    private List<Tag> tagList() {
        return List.of(
                new Tag()
                        .name("Notifications")
                        .description("📧 Core notification management - send, track, and manage notifications across all channels"),
                new Tag()
                        .name("Templates")
                        .description("🎨 Template management - create, edit, and manage notification templates with advanced features"),
                new Tag()
                        .name("Template Content")
                        .description("📝 Content management - rich text content, media, localization, and approval workflows"),
                new Tag()
                        .name("Template Preview")
                        .description("👁️ Template preview - real-time preview, validation, and testing tools"),
                new Tag()
                        .name("User Preferences")
                        .description("⚙️ User preferences - notification settings, opt-out management, and personalization"),
                new Tag()
                        .name("Analytics")
                        .description("📊 Analytics & reporting - delivery metrics, engagement tracking, and performance monitoring"),
                new Tag()
                        .name("Delivery")
                        .description("🚀 Delivery management - multi-channel delivery, status tracking, and queue management"),
                new Tag()
                        .name("Localization")
                        .description("🌍 Localization - multi-language support, translation management, and locale customization"),
                new Tag()
                        .name("Events")
                        .description("🔄 Event-driven operations - event consumers, publishers, and real-time processing")
        );
    }

    private SecurityRequirement securityRequirement() {
        return new SecurityRequirement()
                .addList("Bearer Authentication")
                .addList("Service Authentication")
                .addList("User Authentication");
    }

    private Components securityComponents() {
        return new Components()
                .addSecuritySchemes("User Authentication", userSecurityScheme())
                .addSecuritySchemes("Service Authentication", serviceSecurityScheme())
                .addSecuritySchemes("Bearer Authentication", bearerSecurityScheme());
    }

    private SecurityScheme userSecurityScheme() {
        return new SecurityScheme()
                .name("X-User-ID")
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .description("""
                    **User Authentication** using user identifier header for user-specific operations.

                    **Required for:**
                    - 👤 User notification preferences management
                    - 📜 Personal notification history access
                    - ❌ Opt-out and consent management
                    - 🎯 Personalized content delivery
                    - 📊 User-specific analytics and metrics
                    - 🌍 Language and timezone preferences

                    **Additional Optional Headers:**
                    - `X-User-Email` - User email for validation
                    - `X-User-Language` - Preferred language (e.g., 'en', 'vi')
                    - `X-User-Timezone` - User timezone (e.g., 'Asia/Ho_Chi_Minh')

                    **Example:** `X-User-ID: 12345`
                    """);
    }

    private SecurityScheme serviceSecurityScheme() {
        return new SecurityScheme()
                .name("X-Service-ID")
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .description("""
                    **Service Authentication** using service identifier for inter-microservice communication.

                    **Required for:**
                    - 🔄 Event-driven notification processing
                    - 📦 Bulk notification operations
                    - 🛠️ Administrative and system operations
                    - 📊 Service-level analytics access
                    - 🔧 Health checks and monitoring
                    - 🚀 Automated notification workflows

                    **Valid Service Identifiers:**
                    - `user-service` - User management and authentication
                    - `order-service` - Order lifecycle notifications
                    - `payment-service` - Payment status notifications
                    - `product-catalog-service` - Product updates and recommendations
                    - `shopping-cart-service` - Cart abandonment and reminders
                    - `api-gateway` - Gateway-level operations

                    **Example:** `X-Service-ID: order-service`
                    """);
    }

    private SecurityScheme bearerSecurityScheme() {
        return new SecurityScheme()
                .name("Bearer Authentication")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("""
                    **JWT Bearer Authentication** for enhanced security on sensitive operations.

                    **Required for:**
                    - 🔐 Administrative operations and management
                    - 📊 Advanced analytics and reporting access
                    - 🎨 Template management and content editing
                    - ⚙️ System configuration and settings
                    - 🔧 Bulk operations and data management
                    - 🛡️ Security-sensitive operations

                    **How to use:**
                    1. Obtain JWT token from authentication service
                    2. Include in Authorization header with 'Bearer ' prefix
                    3. Token should contain appropriate roles and permissions

                    **Token Requirements:**
                    - Valid JWT format with proper signature
                    - Non-expired token (check 'exp' claim)
                    - Appropriate roles: 'ADMIN', 'NOTIFICATION_MANAGER', etc.
                    - Valid issuer and audience claims

                    **Example:** `Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`
                    """);
    }
}
