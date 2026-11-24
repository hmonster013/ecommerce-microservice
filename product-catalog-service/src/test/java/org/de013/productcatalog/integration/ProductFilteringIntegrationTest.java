package org.de013.productcatalog.integration;

import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.dto.product.ProductSummaryDto;
import org.de013.productcatalog.entity.Category;
import org.de013.productcatalog.entity.Product;
import org.de013.productcatalog.entity.ProductCategory;
import org.de013.productcatalog.entity.enums.ProductStatus;
import org.de013.productcatalog.repository.CategoryRepository;
import org.de013.productcatalog.repository.ProductCategoryRepository;
import org.de013.productcatalog.repository.ProductRepository;
import org.de013.productcatalog.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductFilteringIntegrationTest {

    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductCategoryRepository productCategoryRepository;

    private Category electronics;
    private Category fashion;

    @BeforeEach
    void setUp() {
        productCategoryRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        electronics = categoryRepository.save(Category.builder()
                .name("Electronics")
                .slug("electronics")
                .isActive(true)
                .build());

        fashion = categoryRepository.save(Category.builder()
                .name("Fashion")
                .slug("fashion")
                .isActive(true)
                .build());

        // Create 25 products with varied statuses, brands, and categories to test pagination & AND filters
        for (int i = 1; i <= 25; i++) {
            ProductStatus status = (i % 3 == 0) ? ProductStatus.INACTIVE : ProductStatus.ACTIVE;
            String brand = (i % 2 == 0) ? "Apple" : "Sony";
            Category cat = (i <= 15) ? electronics : fashion;

            Product p = Product.builder()
                    .name("Test Product " + String.format("%02d", i))
                    .sku("SKU-" + String.format("%04d", i))
                    .price(BigDecimal.valueOf(100 + i))
                    .brand(brand)
                    .status(status)
                    .description("Description for product " + i + (i == 10 ? " specialkeyword" : ""))
                    .shortDescription("Short " + i)
                    .build();

            p = productRepository.save(p);

            ProductCategory pc = ProductCategory.builder()
                    .product(p)
                    .category(cat)
                    .isPrimary(true)
                    .build();
            productCategoryRepository.save(pc);
        }
    }

    @Test
    void testCombinedAndFilters_StatusBrandCategory() {
        // Find: ACTIVE + Apple + Electronics
        // Apple: even i (2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24)
        // Electronics: i <= 15 -> (2, 4, 6, 8, 10, 12, 14)
        // ACTIVE: i % 3 != 0 -> 6 and 12 are INACTIVE, so (2, 4, 8, 10, 14) -> 5 items
        Pageable pageable = PageRequest.of(0, 20);
        PageResponse<ProductSummaryDto> response = productService.getProducts(
                ProductStatus.ACTIVE, "Apple", electronics.getId(), null, pageable
        );

        assertEquals(5, response.getTotalElements());
        for (ProductSummaryDto p : response.getContent()) {
            assertEquals("Apple", p.getBrand());
            assertEquals(ProductStatus.ACTIVE, p.getStatus());
        }
    }

    @Test
    void testOmittedStatus_ReturnsAllStatuses() {
        // status is null -> should return both ACTIVE and INACTIVE
        Pageable pageable = PageRequest.of(0, 50);
        PageResponse<ProductSummaryDto> response = productService.getProducts(
                null, null, null, null, pageable
        );

        assertEquals(25, response.getTotalElements());
        long activeCount = response.getContent().stream().filter(p -> p.getStatus() == ProductStatus.ACTIVE).count();
        long inactiveCount = response.getContent().stream().filter(p -> p.getStatus() == ProductStatus.INACTIVE).count();

        assertTrue(activeCount > 0, "Should contain ACTIVE products");
        assertTrue(inactiveCount > 0, "Should contain INACTIVE products");
    }

    @Test
    void testSearchByQuery_MatchesNameDescriptionOrSku() {
        // Query "specialkeyword" was placed in product 10
        Pageable pageable = PageRequest.of(0, 20);
        PageResponse<ProductSummaryDto> response = productService.getProducts(
                null, null, null, "specialkeyword", pageable
        );

        assertEquals(1, response.getTotalElements());
        assertEquals("SKU-0010", response.getContent().get(0).getSku());
    }

    @Test
    void testPagination_Page1Size10_DiffersFromPage0() {
        Pageable page0 = PageRequest.of(0, 10, Sort.by("name").ascending());
        Pageable page1 = PageRequest.of(1, 10, Sort.by("name").ascending());

        PageResponse<ProductSummaryDto> res0 = productService.getProducts(null, null, null, null, page0);
        PageResponse<ProductSummaryDto> res1 = productService.getProducts(null, null, null, null, page1);

        assertEquals(25, res0.getTotalElements());
        assertEquals(10, res0.getContent().size());
        assertEquals(10, res1.getContent().size());
        assertNotEquals(res0.getContent().get(0).getId(), res1.getContent().get(0).getId());
    }

    @Test
    void testDistinct_WhenProductLinkedToMultipleCategories() {
        // Link product 1 to fashion as well
        Product p1 = productRepository.findAll().get(0);
        ProductCategory pcExtra = ProductCategory.builder()
                .product(p1)
                .category(fashion)
                .isPrimary(false)
                .build();
        productCategoryRepository.save(pcExtra);

        // Search with q matching product 1
        Pageable pageable = PageRequest.of(0, 20);
        PageResponse<ProductSummaryDto> response = productService.getProducts(
                null, null, null, p1.getName(), pageable
        );

        // Should return exactly 1 item, no duplicate row
        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getContent().size());
    }
}
