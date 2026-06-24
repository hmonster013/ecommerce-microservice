package org.de013.productcatalog.integration;

import org.de013.productcatalog.entity.Category;
import org.de013.productcatalog.entity.Inventory;
import org.de013.productcatalog.entity.Product;
import org.de013.productcatalog.entity.enums.ProductStatus;
import org.de013.productcatalog.repository.CategoryRepository;
import org.de013.productcatalog.repository.InventoryRepository;
import org.de013.productcatalog.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Test
    void testCreateProductAndInventory() {
        Category category = Category.builder()
                .name("Electronics")
                .slug("electronics")
                .isActive(true)
                .build();
        category = categoryRepository.save(category);
        assertNotNull(category.getId());

        Product product = Product.builder()
                .name("New Phone")
                .sku("PHONE-12345")
                .price(BigDecimal.valueOf(699.99))
                .status(ProductStatus.ACTIVE)
                .build();
        product = productRepository.save(product);
        assertNotNull(product.getId());

        Inventory inventory = Inventory.builder()
                .product(product)
                .quantity(50)
                .reservedQuantity(0)
                .minStockLevel(5)
                .build();
        product.setInventory(inventory);
        inventory = inventoryRepository.save(inventory);
        assertNotNull(inventory.getId());

        Optional<Product> fetchedProduct = productRepository.findBySku("PHONE-12345");
        assertTrue(fetchedProduct.isPresent());
        assertEquals("New Phone", fetchedProduct.get().getName());
        assertNotNull(fetchedProduct.get().getInventory());
        assertEquals(50, fetchedProduct.get().getInventory().getQuantity());
    }
}
