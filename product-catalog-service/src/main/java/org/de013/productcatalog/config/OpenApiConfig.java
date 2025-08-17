package org.de013.productcatalog.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port}")
    private String serverPort;

    @Bean
    public OpenAPI productCatalogOpenAPI() {
        Server devServer = new Server();
        devServer.setUrl("http://localhost:" + serverPort);
        devServer.setDescription("Development server");

        Server prodServer = new Server();
        prodServer.setUrl("http://localhost:8080/api/v1");
        prodServer.setDescription("Production server (via API Gateway)");

        Contact contact = new Contact();
        contact.setEmail("admin@ecommerce.com");
        contact.setName("E-commerce Team");
        contact.setUrl("https://github.com/de013/ecommerce-microservice");

        License license = new License()
                .name("MIT License")
                .url("https://choosealicense.com/licenses/mit/");

        Info info = new Info()
                .title("Product Catalog Service API")
                .version("1.0.0")
                .contact(contact)
                .description("This API provides endpoints for managing products, categories, inventory, and reviews in the e-commerce platform.")
                .termsOfService("https://ecommerce.com/terms")
                .license(license);

        return new OpenAPI()
                .info(info)
                .servers(List.of(devServer, prodServer));
    }
}
