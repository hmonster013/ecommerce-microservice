package org.de013.orderservice.integration;

import org.de013.orderservice.repository.CheckoutAttemptRepository;
import org.de013.orderservice.service.impl.CheckoutAttemptStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.cloud.bus.enabled=false", "spring.jpa.open-in-view=false"})
@ActiveProfiles("test")
class CheckoutAttemptStoreTest {
    @Autowired CheckoutAttemptStore store;
    @Autowired CheckoutAttemptRepository repository;
    @Autowired org.de013.orderservice.repository.OrderRepository orders;
    @Autowired org.de013.orderservice.service.impl.CheckoutOrderService checkout;

    @Test void reservationCommitsIndependentlyAndCannotBeOverwritten() {
        String key = UUID.randomUUID().toString();
        try {
            store.reserve(key, "owner");
            assertThrows(DataIntegrityViolationException.class, () -> store.reserve(key, "other"));
            assertEquals("owner", repository.findById(key).orElseThrow().getUserId());
        } finally { repository.deleteById(key); }
    }

    @Test void repeatedAttemptMapsLazyOrderItemsInsideReadTransactionWithOsivDisabled() {
        var key = UUID.randomUUID();
        var address = org.de013.orderservice.entity.valueobject.Address.builder()
                .firstName("Test").lastName("Owner").streetAddress("123 Test Street")
                .city("Hanoi").state("Hanoi").postalCode("100000").country("VN").build();
        var order = org.de013.orderservice.entity.Order.builder().checkoutKey(key.toString())
                .orderNumber("ORD-" + key.toString().substring(0, 8)).userId("owner")
                .status(org.de013.orderservice.entity.enums.OrderStatus.PENDING)
                .orderType(org.de013.orderservice.entity.enums.OrderType.STANDARD)
                .totalAmount(org.de013.orderservice.entity.valueobject.Money.of(1.00, "USD"))
                .subtotalAmount(org.de013.orderservice.entity.valueobject.Money.of(1.00, "USD"))
                .shippingAddress(address).build();
        store.reserve(key.toString(), "owner");
        var saved = orders.saveAndFlush(order);
        try {
            // No test transaction/OSIV: repository read returns detached lazy collections, as in production.
            assertEquals(saved.getId(), checkout.read(key, "owner").order().id());
            var request = new org.de013.orderservice.dto.request.CreateOrderRequest(null, 1L,
                    org.de013.orderservice.entity.enums.OrderType.STANDARD, address, address, "CARD", "USD", null);
            assertEquals(saved.getId(), checkout.create(key, "owner", request).order().id());
        } finally { orders.deleteById(saved.getId()); repository.deleteById(key.toString()); }
    }
}
