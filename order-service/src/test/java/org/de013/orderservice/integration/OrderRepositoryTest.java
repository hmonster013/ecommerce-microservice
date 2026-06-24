package org.de013.orderservice.integration;

import org.de013.orderservice.entity.Order;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.entity.enums.OrderType;
import org.de013.orderservice.entity.valueobject.Address;
import org.de013.orderservice.entity.valueobject.Money;
import org.de013.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void testCreateAndFindOrder() {
        Address address = Address.builder()
                .firstName("John")
                .lastName("Doe")
                .streetAddress("123 Main St")
                .city("Hanoi")
                .state("Hanoi")
                .postalCode("100000")
                .country("VN")
                .phone("+84987654321")
                .email("john@example.com")
                .build();

        Order order = Order.builder()
                .orderNumber("ORD-9999")
                .userId("user-uuid-9999")
                .status(OrderStatus.PENDING)
                .orderType(OrderType.STANDARD)
                .totalAmount(Money.of(150.00, "USD"))
                .subtotalAmount(Money.of(150.00, "USD"))
                .shippingAddress(address)
                .build();

        Order saved = orderRepository.save(order);
        assertNotNull(saved.getId());

        Optional<Order> fetchedOpt = orderRepository.findByOrderNumber("ORD-9999");
        assertTrue(fetchedOpt.isPresent());
        assertEquals("user-uuid-9999", fetchedOpt.get().getUserId());
        assertEquals(BigDecimal.valueOf(150.00), fetchedOpt.get().getTotalAmount().getAmount());

        Page<Order> page = orderRepository.findByUserId("user-uuid-9999", PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
    }
}
