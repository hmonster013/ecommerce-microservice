package org.de013.productcatalog.integration;

import org.de013.productcatalog.dto.product.ProductImageDto;
import org.de013.productcatalog.dto.product.ProductSummaryDto;
import org.de013.productcatalog.entity.Product;
import org.de013.productcatalog.entity.ProductImage;
import org.de013.productcatalog.entity.enums.ImageType;
import org.de013.productcatalog.entity.enums.ProductStatus;
import org.de013.productcatalog.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductMapperImageFallbackTest {

    private ProductMapper productMapper;

    @BeforeEach
    void setUp() {
        CategoryMapper categoryMapper = new CategoryMapper();
        ProductImageMapper productImageMapper = new ProductImageMapper();
        ProductVariantMapper productVariantMapper = new ProductVariantMapper();
        InventoryMapper inventoryMapper = new InventoryMapper();

        productMapper = new ProductMapper(
                categoryMapper,
                productImageMapper,
                productVariantMapper,
                inventoryMapper
        );
    }

    @Test
    void testGetMainImageUrl_PrefersActiveMainImage() {
        Product product = Product.builder()
                .id(1L)
                .name("Test")
                .sku("TEST-001")
                .price(BigDecimal.TEN)
                .status(ProductStatus.ACTIVE)
                .build();

        ProductImage inactiveMain = ProductImage.builder()
                .id(1L)
                .url("http://example.com/inactive-main.jpg")
                .imageType(ImageType.MAIN)
                .isActive(false)
                .displayOrder(1)
                .build();

        ProductImage activeGallery = ProductImage.builder()
                .id(2L)
                .url("http://example.com/active-gallery.jpg")
                .imageType(ImageType.GALLERY)
                .isActive(true)
                .displayOrder(2)
                .build();

        ProductImage activeMain = ProductImage.builder()
                .id(3L)
                .url("http://example.com/active-main.jpg")
                .imageType(ImageType.MAIN)
                .isActive(true)
                .displayOrder(3)
                .build();

        product.setImages(new ArrayList<>(List.of(inactiveMain, activeGallery, activeMain)));

        ProductSummaryDto summary = productMapper.toProductSummaryDto(product);

        // Must pick activeMain over inactiveMain, even though inactiveMain is first in list
        assertEquals("http://example.com/active-main.jpg", summary.getMainImageUrl());
    }

    @Test
    void testGetMainImageUrl_FallsBackToFirstActiveByDisplayOrder_NeverInactive() {
        Product product = Product.builder()
                .id(1L)
                .name("Test")
                .sku("TEST-001")
                .price(BigDecimal.TEN)
                .status(ProductStatus.ACTIVE)
                .build();

        ProductImage inactiveMain = ProductImage.builder()
                .id(1L)
                .url("http://example.com/inactive-main.jpg")
                .imageType(ImageType.MAIN)
                .isActive(false)
                .displayOrder(1)
                .build();

        ProductImage activeGallery2 = ProductImage.builder()
                .id(2L)
                .url("http://example.com/active-gallery-order2.jpg")
                .imageType(ImageType.GALLERY)
                .isActive(true)
                .displayOrder(2)
                .build();

        ProductImage activeGallery1 = ProductImage.builder()
                .id(3L)
                .url("http://example.com/active-gallery-order1.jpg")
                .imageType(ImageType.GALLERY)
                .isActive(true)
                .displayOrder(1)
                .build();

        // No active MAIN image exists
        product.setImages(new ArrayList<>(List.of(inactiveMain, activeGallery2, activeGallery1)));

        ProductSummaryDto summary = productMapper.toProductSummaryDto(product);

        // Must fall back to active gallery with lowest displayOrder (activeGallery1), never inactiveMain
        assertEquals("http://example.com/active-gallery-order1.jpg", summary.getMainImageUrl());
    }

    @Test
    void testGetMainImageUrl_ReturnsNullWhenAllImagesInactive() {
        Product product = Product.builder()
                .id(1L)
                .name("Test")
                .sku("TEST-001")
                .price(BigDecimal.TEN)
                .status(ProductStatus.ACTIVE)
                .build();

        ProductImage inactive1 = ProductImage.builder()
                .id(1L)
                .url("http://example.com/inactive-1.jpg")
                .imageType(ImageType.MAIN)
                .isActive(false)
                .displayOrder(1)
                .build();

        ProductImage inactive2 = ProductImage.builder()
                .id(2L)
                .url("http://example.com/inactive-2.jpg")
                .imageType(ImageType.GALLERY)
                .isActive(false)
                .displayOrder(2)
                .build();

        product.setImages(new ArrayList<>(List.of(inactive1, inactive2)));

        ProductSummaryDto summary = productMapper.toProductSummaryDto(product);

        // All images inactive -> must return null, never leak inactive images to public
        assertNull(summary.getMainImageUrl());
    }
}
