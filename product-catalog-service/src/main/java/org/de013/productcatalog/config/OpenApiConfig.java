package org.de013.productcatalog.config;

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
 * OpenAPI/Swagger configuration for Product Catalog Service API documentation.
 */

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8082}")
    private String serverPort;

    @Value("${spring.application.name:product-catalog-service}")
    private String applicationName;

    @Bean
    public OpenAPI productCatalogOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Product Catalog Service API")
                        .description("""
                                **Enterprise Product Catalog Service** - A comprehensive microservice for managing product catalogs with advanced features:

                                ## 🚀 Key Features
                                - **Product Management**: Complete CRUD operations for products, categories, and variants
                                - **Advanced Search**: Full-text search with filtering, sorting, and pagination
                                - **Inventory Management**: Real-time stock tracking and availability
                                - **Review System**: Customer reviews and ratings management
                                - **Caching**: Redis-based multi-layer caching for optimal performance
                                - **Analytics**: Search analytics and business intelligence

                                ## 🔍 Search Capabilities
                                - Multi-field text search across products
                                - Dynamic filtering by price, category, brand, rating
                                - Advanced sorting options
                                - Search suggestions and autocomplete
                                - Popular and trending products

                                ## ⚡ Performance Features
                                - Redis caching with intelligent TTL strategies
                                - Database connection pooling (HikariCP)
                                - JPA performance optimizations
                                - Search result caching and optimization

                                ## 📊 Analytics & Monitoring
                                - Search behavior tracking
                                - Performance metrics and KPIs
                                - Business intelligence insights
                                - Health monitoring and diagnostics
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Product Catalog Team")
                                .email("product-catalog@de013.org")
                                .url("https://github.com/de013/ecommerce-microservice"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Direct Service Access (Development)"),
                        new Server()
                                .url("http://localhost:8080/api/v1/productsv")
                                .description("API Gateway (Development)")))
                .tags(List.of(
                        new Tag()
                                .name("Products")
                                .description("Product management operations - Create, read, update, and delete products"),
                        new Tag()
                                .name("Categories")
                                .description("Category management operations - Hierarchical category structure"),
                        new Tag()
                                .name("Search")
                                .description("Advanced search operations - Full-text search, filtering, and sorting"),
                        new Tag()
                                .name("Inventory")
                                .description("Inventory management operations - Stock tracking and availability"),
                        new Tag()
                                .name("Reviews")
                                .description("Review management operations - Customer reviews and ratings")))
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
