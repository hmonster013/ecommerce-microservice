package org.de013.productcatalog.service;

import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.dto.product.*;
import org.de013.productcatalog.dto.search.ProductSearchDto;
import org.de013.productcatalog.dto.search.SearchResultDto;
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
    SearchResultDto searchProducts(ProductSearchDto searchDto);
    
    PageResponse<ProductSummaryDto> searchProductsSimple(String query, Pageable pageable);
    
    PageResponse<ProductSummaryDto> fullTextSearch(String query, Pageable pageable);

    // Product Relationships
    List<ProductSummaryDto> getRelatedProducts(Long productId);
    
    List<ProductSummaryDto> getRelatedProducts(Long productId, int limit);
    
    List<ProductSummaryDto> getProductsInSameCategory(Long productId);

    // Product Status Operations
    ProductResponseDto activateProduct(Long id);
    
    ProductResponseDto deactivateProduct(Long id);
    
    ProductResponseDto discontinueProduct(Long id);
    
    List<ProductResponseDto> bulkUpdateStatus(List<Long> productIds, ProductStatus status);

    // Featured Product Operations
    ProductResponseDto setFeatured(Long id, boolean featured);
    
    List<ProductResponseDto> bulkSetFeatured(List<Long> productIds, boolean featured);

    // Product Validation
    boolean isSkuUnique(String sku);
    
    boolean isSkuUnique(String sku, Long excludeProductId);
    
    void validateProductData(ProductCreateDto createDto);
    
    void validateProductData(ProductUpdateDto updateDto, Long productId);

    // Product Statistics
    long getTotalProductCount();
    
    long getActiveProductCount();
    
    long getFeaturedProductCount();
    
    long getProductCountByCategory(Long categoryId);
    
    long getProductCountByBrand(String brand);

    // Recent Products
    List<ProductSummaryDto> getRecentProducts();
    
    List<ProductSummaryDto> getRecentProducts(int limit);
    
    PageResponse<ProductSummaryDto> getRecentProducts(Pageable pageable);

    // Products on Sale
    PageResponse<ProductSummaryDto> getProductsOnSale(Pageable pageable);
    
    List<ProductSummaryDto> getProductsOnSale(int limit);

    // In Stock Products
    PageResponse<ProductSummaryDto> getInStockProducts(Pageable pageable);
    
    List<ProductSummaryDto> getLowStockProducts();

    // Brand Operations
    List<String> getAllBrands();
    
    List<String> getActiveBrands();
    
    PageResponse<ProductSummaryDto> getProductsByBrands(List<String> brands, Pageable pageable);

    // Category Operations
    PageResponse<ProductSummaryDto> getProductsByCategories(List<Long> categoryIds, Pageable pageable);
    
    long getProductCountByCategories(List<Long> categoryIds);

    // Price Range Operations
    PageResponse<ProductSummaryDto> getProductsByPriceRange(java.math.BigDecimal minPrice, 
                                                           java.math.BigDecimal maxPrice, 
                                                           Pageable pageable);

    // Product Existence Checks
    boolean existsById(Long id);
    
    boolean existsBySku(String sku);

    // Cache Operations
    void clearProductCache();
    
    void clearProductCache(Long productId);
    
    void refreshProductCache(Long productId);

    // Bulk Operations
    List<ProductSummaryDto> getProductsByIds(List<Long> productIds);
    
    void bulkDeleteProducts(List<Long> productIds);
}
