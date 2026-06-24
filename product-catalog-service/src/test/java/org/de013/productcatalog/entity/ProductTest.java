package org.de013.productcatalog.entity;

import org.de013.productcatalog.entity.enums.ProductStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ProductTest {

    @Test
    void testIsOnSale_WhenComparePriceIsGreater_ShouldBeTrue() {
        Product product = Product.builder()
                .price(BigDecimal.valueOf(100.00))
                .comparePrice(BigDecimal.valueOf(150.00))
                .status(ProductStatus.ACTIVE)
                .build();

        assertTrue(product.isOnSale());
        assertEquals(0, BigDecimal.valueOf(50.00).compareTo(product.getDiscountAmount()));
        assertEquals(0, BigDecimal.valueOf(33.33).compareTo(product.getDiscountPercentage().setScale(2, BigDecimal.ROUND_HALF_UP)));
    }

    @Test
    void testIsOnSale_WhenComparePriceIsLessOrEqual_ShouldBeFalse() {
        Product product = Product.builder()
                .price(BigDecimal.valueOf(100.00))
                .comparePrice(BigDecimal.valueOf(100.00))
                .status(ProductStatus.ACTIVE)
                .build();

        assertFalse(product.isOnSale());
        assertEquals(BigDecimal.ZERO, product.getDiscountAmount());
        assertEquals(BigDecimal.ZERO, product.getDiscountPercentage());
    }
}
