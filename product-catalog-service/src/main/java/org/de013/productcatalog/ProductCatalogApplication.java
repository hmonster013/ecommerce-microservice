package org.de013.productcatalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class ProductCatalogApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductCatalogApplication.class, args);
    }
}
