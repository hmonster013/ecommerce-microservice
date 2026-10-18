package org.de013.productcatalog.service.support;

import org.de013.productcatalog.dto.category.CategoryCreateDto;
import org.de013.productcatalog.dto.category.CategoryResponseDto;
import org.de013.productcatalog.dto.image.ProductImageCreateDto;
import org.de013.productcatalog.dto.product.ProductCreateDto;
import org.de013.productcatalog.dto.product.ProductResponseDto;
import org.de013.productcatalog.dto.product.ProductUpdateDto;
import org.de013.productcatalog.dto.variant.ProductVariantCreateDto;
import org.de013.productcatalog.entity.enums.ImageType;
import org.de013.productcatalog.entity.enums.ProductStatus;
import org.de013.productcatalog.entity.enums.VariantType;
import org.de013.productcatalog.service.CategoryService;
import org.de013.productcatalog.service.ProductImageService;
import org.de013.productcatalog.service.ProductService;
import org.de013.productcatalog.service.ProductVariantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductCatalogCacheEvictionIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductImageService productImageService;

    @Autowired
    private ProductVariantService productVariantService;

    @MockBean(name = "cacheManager")
    private CacheManager cacheManager;

    private CacheManager localCaches;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        localCaches = new ConcurrentMapCacheManager(
                "products", "categories", "featured", "images", "variants", "inventory", "variantInventory"
        );
        when(cacheManager.getCache(anyString())).thenAnswer(call -> localCaches.getCache(call.getArgument(0)));

        CategoryCreateDto catDto = new CategoryCreateDto(
                "Cache Test Category",
                "cache-test-cat",
                "Description",
                null,
                1,
                true
        );
        CategoryResponseDto cat = categoryService.createCategory(catDto);
        categoryId = cat.getId();
    }

    @Test
    void productUpdateEvictsProductsFeaturedAndCategoriesCache() {
        // 1. Create product
        ProductCreateDto createDto = ProductCreateDto.builder()
                .name("Cacheable Laptop")
                .sku("LAP-0001")
                .price(new BigDecimal("1000.00"))
                .status(ProductStatus.ACTIVE)
                .isFeatured(true)
                .categoryIds(List.of(categoryId))
                .primaryCategoryId(categoryId)
                .build();
        ProductResponseDto created = productService.createProduct(createDto);
        Long pId = created.getId();

        // 2. Warm caches
        var detailById = productService.getProductById(pId);
        var detailBySku = productService.getProductBySku("LAP-0001");
        var featuredList = productService.getFeaturedProducts();
        assertNotNull(detailById);
        assertNotNull(detailBySku);
        assertFalse(featuredList.isEmpty());

        assertNotNull(localCaches.getCache("products").get(pId));
        assertNotNull(localCaches.getCache("products").get("LAP-0001"));
        assertNotNull(localCaches.getCache("featured").get("all"));

        // 3. Mutate product
        ProductUpdateDto updateDto = ProductUpdateDto.builder()
                .name("Updated Laptop Pro")
                .price(new BigDecimal("1200.00"))
                .build();
        productService.updateProduct(pId, updateDto);

        // 4. Verify cache eviction
        assertNull(localCaches.getCache("products").get(pId));
        assertNull(localCaches.getCache("products").get("LAP-0001"));
        assertNull(localCaches.getCache("featured").get("all"));

        // 5. Subsequent read returns updated data
        var freshDetail = productService.getProductById(pId);
        assertEquals("Updated Laptop Pro", freshDetail.getName());
    }

    @Test
    void imageMutationEvictsProductsAndImagesCache() {
        ProductCreateDto createDto = ProductCreateDto.builder()
                .name("Camera Device")
                .sku("CAM-0001")
                .price(new BigDecimal("500.00"))
                .status(ProductStatus.ACTIVE)
                .categoryIds(List.of(categoryId))
                .primaryCategoryId(categoryId)
                .build();
        ProductResponseDto created = productService.createProduct(createDto);
        Long pId = created.getId();

        // Warm product cache
        productService.getProductById(pId);
        assertNotNull(localCaches.getCache("products").get(pId));

        // Add image
        ProductImageCreateDto imgDto = new ProductImageCreateDto(
                pId,
                "https://example.com/cam.jpg",
                "Camera front",
                ImageType.MAIN,
                1,
                true,
                "Title",
                "Desc",
                "1MB",
                "1920x1080",
                "JPG",
                null
        );
        var createdImg = productImageService.createImage(pId, imgDto);
        assertNotNull(createdImg);

        // Verify products cache was evicted so detail page gets latest image list
        assertNull(localCaches.getCache("products").get(pId));
    }

    @Test
    void variantMutationEvictsProductsAndVariantsCache() {
        ProductCreateDto createDto = ProductCreateDto.builder()
                .name("T-Shirt")
                .sku("TSH-0001")
                .price(new BigDecimal("25.00"))
                .status(ProductStatus.ACTIVE)
                .categoryIds(List.of(categoryId))
                .primaryCategoryId(categoryId)
                .build();
        ProductResponseDto created = productService.createProduct(createDto);
        Long pId = created.getId();

        // Warm product cache
        productService.getProductById(pId);
        assertNotNull(localCaches.getCache("products").get(pId));

        // Add variant
        ProductVariantCreateDto vDto = new ProductVariantCreateDto(
                pId,
                VariantType.SIZE,
                "Size",
                "XL",
                BigDecimal.ZERO,
                "TSH-XL",
                1,
                true,
                null,
                "Extra Large"
        );
        var createdV = productVariantService.createVariant(pId, vDto);
        assertNotNull(createdV);

        // Verify products cache was evicted so detail page gets latest variants
        assertNull(localCaches.getCache("products").get(pId));
    }
}
