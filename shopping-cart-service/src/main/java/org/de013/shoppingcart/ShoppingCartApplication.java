package org.de013.shoppingcart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * Shopping Cart Service Application
 *
 * A high-performance microservice for managing shopping carts in an e-commerce platform.
 * Features Redis-based primary storage, PostgreSQL backup, and comprehensive cart management.
 *
 * Key Features:
 * - Ultra-fast Redis-based cart operations
 * - Session management for guest and authenticated users
 * - Real-time product validation and pricing
 * - Bulk operations and cart merging
 * - Comprehensive caching strategies
 *
 * @author E-commerce Development Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@ComponentScan(basePackages = {
    "org.de013.shoppingcart",
    "org.de013.common"
})
public class ShoppingCartApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoppingCartApplication.class, args);
    }
}
