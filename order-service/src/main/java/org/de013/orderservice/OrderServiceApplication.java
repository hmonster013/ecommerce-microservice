package org.de013.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Order Service Application
 *
 * Main application class for the Order Management microservice.
 * This service handles comprehensive order lifecycle management including:
 * - Order creation from shopping carts
 * - Order processing and fulfillment
 * - Order tracking and status updates
 * - Payment integration and processing
 * - Inventory management integration
 * - Shipping and delivery coordination
 * - Order analytics and reporting
 *
 * @author Development Team
 * @version 1.0.0
 */
@SpringBootApplication(scanBasePackages = {
    "org.de013.orderservice",
    "org.de013.common"
})
@EnableDiscoveryClient
@EnableJpaAuditing
@EnableTransactionManagement
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
