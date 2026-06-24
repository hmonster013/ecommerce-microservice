package org.de013.shoppingcart.integration;

import org.de013.shoppingcart.config.TestRedisConfig;
import org.de013.shoppingcart.entity.Cart;
import org.de013.shoppingcart.entity.CartItem;
import org.de013.shoppingcart.entity.enums.CartStatus;
import org.de013.shoppingcart.entity.enums.CartType;
import org.de013.shoppingcart.repository.jpa.CartItemRepository;
import org.de013.shoppingcart.repository.jpa.CartRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@Transactional
class CartRepositoryTest {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Test
    void testCreateAndFindCartWithItems() {
        // Create a new active cart
        Cart cart = Cart.builder()
                .userId("user-uuid-1111")
                .sessionId("session-xyz")
                .status(CartStatus.ACTIVE)
                .cartType(CartType.USER)
                .currency("USD")
                .build();

        // Add an item to the cart
        CartItem item = CartItem.builder()
                .productId("product-uuid-2222")
                .productName("Awesome Product")
                .productBrand("TechBrand")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(100.00))
                .addedAt(LocalDateTime.now())
                .build();

        item.calculateTotalPrice();
        cart.addItem(item);

        // Save cart (cascade should persist item)
        Cart savedCart = cartRepository.save(cart);
        assertNotNull(savedCart.getId());
        assertEquals(1, savedCart.getCartItems().size());

        // Verify total calculations
        assertEquals(BigDecimal.valueOf(200.00), savedCart.getSubtotal());
        assertEquals(BigDecimal.valueOf(200.00), savedCart.getTotalAmount());

        // Retrieve from repository
        Optional<Cart> retrieved = cartRepository.findByUserIdAndStatus(
                "user-uuid-1111", CartStatus.ACTIVE);
        assertTrue(retrieved.isPresent());
        assertEquals(savedCart.getId(), retrieved.get().getId());
        assertEquals(1, retrieved.get().getCartItems().size());
        assertEquals("Awesome Product", retrieved.get().getCartItems().get(0).getProductName());
    }
}
