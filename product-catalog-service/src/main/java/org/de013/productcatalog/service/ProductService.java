package org.de013.productcatalog.service;

import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.dto.product.*;

import org.de013.productcatalog.entity.enums.ProductStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductService {

    // CRUD Operations
    ProductResponseDto createProduct(ProductCreateDto createDto);
    
    ProductResponseDto updateProduct(Long id, ProductUpdateDto updateDto);
    
    void deleteProduct(Long id);
    
    ProductDetailDto getProductById(Long id);
    
    ProductDetailDto getProductBySku(String sku);

    // Listing Operations
    PageResponse<ProductSummaryDto> getAllProducts(Pageable pageable);
    
    PageResponse<ProductSummaryDto> getProductsByStatus(ProductStatus status, Pageable pageable);
    
    PageResponse<ProductSummaryDto> getProductsByCategory(Long categoryId, Pageable pageable);
    
    PageResponse<ProductSummaryDto> getProductsByCategorySlug(String categorySlug, Pageable pageable);
    
    PageResponse<ProductSummaryDto> getProductsByBrand(String brand, Pageable pageable);

    // Featured Products
    List<ProductSummaryDto> getFeaturedProducts();
    
    List<ProductSummaryDto> getFeaturedProducts(int limit);
    
    List<ProductSummaryDto> getFeaturedProductsByCategory(Long categoryId);
    
    List<ProductSummaryDto> getFeaturedProductsByCategory(Long categoryId, int limit);

    // Search Operations
    PageResponse<ProductSummaryDto> searchProductsSimple(String query, Pageable pageable);
    
    PageResponse<ProductSummaryDto> fullTextSearch(String query, Pageable pageable);

    // Product Validation
    boolean isSkuUnique(String sku);

    boolean isSkuUnique(String sku, Long excludeProductId);

    void validateProductData(ProductCreateDto createDto);

    void validateProductData(ProductUpdateDto updateDto, Long productId);

    // Product Statistics (Essential only)
    long getTotalProductCount();

    long getActiveProductCount();

    long getFeaturedProductCount();

    // Product Existence Checks
    boolean existsById(Long id);

    boolean existsBySku(String sku);
}
