package org.de013.shoppingcart.entity;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class CartItemTest {

    @Test
    void calculateTotalPrice_WithQuantityAndUnitPrice_ShouldCalculateCorrectly() {
        CartItem item = CartItem.builder()
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(999.00))
                .build();

        item.calculateTotalPrice();

        assertEquals(BigDecimal.valueOf(1998.00), item.getTotalPrice());
    }

    @Test
    void calculateTotalPrice_WithDiscountPerUnit_ShouldApplyDiscountOncePerUnit() {
        // Regression R2 money double-discount: discount is per-unit
        CartItem item = CartItem.builder()
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(999.00))
                .discountAmount(BigDecimal.valueOf(100.00)) // discount per unit
                .build();

        item.calculateTotalPrice();

        // (999 - 100) * 2 = 1798
        assertEquals(BigDecimal.valueOf(1798.00), item.getTotalPrice());
    }

    @Test
    void calculateTotalPrice_WithGiftWrap_ShouldAddGiftWrapPrice() {
        CartItem item = CartItem.builder()
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(999.00))
                .isGift(true)
                .giftWrapPrice(BigDecimal.valueOf(5.00))
                .build();

        item.calculateTotalPrice();

        assertEquals(BigDecimal.valueOf(1004.00), item.getTotalPrice());
    }

    @Test
    void calculateTotalPrice_WithNullValues_ShouldSetToZero() {
        CartItem item = CartItem.builder()
                .quantity(null)
                .unitPrice(null)
                .build();

        item.calculateTotalPrice();

        assertEquals(BigDecimal.ZERO, item.getTotalPrice());
    }

    @Test
    void getDiscountPercentage_BoundaryCases_ShouldReturnCorrectPercentage() {
        CartItem item = CartItem.builder()
                .originalPrice(BigDecimal.valueOf(100.00))
                .discountAmount(BigDecimal.valueOf(20.00))
                .build();

        assertEquals(0, BigDecimal.valueOf(20.00).compareTo(item.getDiscountPercentage()));

        CartItem nullOriginal = CartItem.builder()
                .originalPrice(null)
                .discountAmount(BigDecimal.valueOf(20.00))
                .build();
        assertEquals(BigDecimal.ZERO, nullOriginal.getDiscountPercentage());

        CartItem zeroOriginal = CartItem.builder()
                .originalPrice(BigDecimal.ZERO)
                .discountAmount(BigDecimal.valueOf(20.00))
                .build();
        assertEquals(BigDecimal.ZERO, zeroOriginal.getDiscountPercentage());
    }
}
