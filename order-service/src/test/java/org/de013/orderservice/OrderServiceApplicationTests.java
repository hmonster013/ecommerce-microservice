package org.de013.orderservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Basic integration test for Order Service Application
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderServiceApplicationTests {

    @Test
    void contextLoads() {
        // This test verifies that the Spring application context loads successfully
        // with all the configured beans and dependencies
    }
}
