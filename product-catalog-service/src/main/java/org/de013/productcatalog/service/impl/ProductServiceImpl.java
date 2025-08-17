package org.de013.productcatalog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.dto.product.*;
import org.de013.productcatalog.dto.search.ProductSearchDto;
import org.de013.productcatalog.dto.search.SearchResultDto;
import org.de013.productcatalog.entity.*;
import org.de013.productcatalog.entity.enums.ProductStatus;
import org.de013.productcatalog.repository.*;
import org.de013.productcatalog.repository.specification.ProductSpecification;
import org.de013.productcatalog.service.ProductService;
import org.de013.productcatalog.util.EntityMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final InventoryRepository inventoryRepository;
    private final EntityMapper entityMapper;

    @Override
    @Transactional
    public ProductResponseDto createProduct(ProductCreateDto createDto) {
        log.info("Creating product with SKU: {}", createDto.getSku());
        
        validateProductData(createDto);
        
        // Create product entity
        Product product = Product.builder()
                .name(createDto.getName())
                .description(createDto.getDescription())
                .shortDescription(createDto.getShortDescription())
                .sku(createDto.getSku())
                .price(createDto.getPrice())
                .comparePrice(createDto.getComparePrice())
                .costPrice(createDto.getCostPrice())
                .brand(createDto.getBrand())
                .weight(createDto.getWeight())
                .dimensions(createDto.getDimensions())
                .status(createDto.getStatus())
                .isFeatured(createDto.getIsFeatured())
                .isDigital(createDto.getIsDigital())
                .requiresShipping(createDto.getRequiresShipping())
                .metaTitle(createDto.getMetaTitle())
                .metaDescription(createDto.getMetaDescription())
                .searchKeywords(createDto.getSearchKeywords())
                .build();

        product = productRepository.save(product);
        
        // Create product-category relationships
        createProductCategoryRelationships(product, createDto.getCategoryIds(), createDto.getPrimaryCategoryId());
        
        // Create initial inventory if specified
        if (createDto.getInitialQuantity() != null && createDto.getInitialQuantity() > 0) {
            createInitialInventory(product, createDto);
        }
        
        log.info("Product created successfully with ID: {}", product.getId());
        return entityMapper.toProductResponseDto(product);
    }

    @Override
    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public ProductResponseDto updateProduct(Long id, ProductUpdateDto updateDto) {
        log.info("Updating product with ID: {}", id);
        
        Product product = findProductById(id);
        validateProductData(updateDto, id);
        
        // Update product fields
        updateProductFields(product, updateDto);
        
        // Update category relationships if provided
        if (updateDto.getCategoryIds() != null) {
            updateProductCategoryRelationships(product, updateDto.getCategoryIds(), updateDto.getPrimaryCategoryId());
        }
        
        product = productRepository.save(product);
        
        log.info("Product updated successfully with ID: {}", id);
        return entityMapper.toProductResponseDto(product);
    }

    @Override
    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public void deleteProduct(Long id) {
        log.info("Deleting product with ID: {}", id);
        
        Product product = findProductById(id);
        productRepository.delete(product);
        
        log.info("Product deleted successfully with ID: {}", id);
    }

    @Override
    @Cacheable(value = "products", key = "#id")
    public ProductDetailDto getProductById(Long id) {
        log.debug("Getting product by ID: {}", id);
        
        Product product = findProductById(id);
        return entityMapper.toProductDetailDto(product);
    }

    @Override
    @Cacheable(value = "products", key = "#sku")
    public ProductDetailDto getProductBySku(String sku) {
        log.debug("Getting product by SKU: {}", sku);
        
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new RuntimeException("Product not found with SKU: " + sku));
        
        return entityMapper.toProductDetailDto(product);
    }

    @Override
    public PageResponse<ProductSummaryDto> getAllProducts(Pageable pageable) {
        log.debug("Getting all products with pagination: {}", pageable);
        
        Page<Product> products = productRepository.findAll(pageable);
        return mapToPageResponse(products);
    }

    @Override
    public PageResponse<ProductSummaryDto> getProductsByStatus(ProductStatus status, Pageable pageable) {
        log.debug("Getting products by status: {} with pagination: {}", status, pageable);
        
        Page<Product> products = productRepository.findByStatus(status, pageable);
        return mapToPageResponse(products);
    }

    @Override
    @Cacheable(value = "categories", key = "'products_' + #categoryId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public PageResponse<ProductSummaryDto> getProductsByCategory(Long categoryId, Pageable pageable) {
        log.debug("Getting products by category ID: {} with pagination: {}", categoryId, pageable);
        
        Page<Product> products = productRepository.findByCategoryIdAndStatus(
                categoryId, ProductStatus.ACTIVE, pageable);
        return mapToPageResponse(products);
    }

    @Override
    public PageResponse<ProductSummaryDto> getProductsByCategorySlug(String categorySlug, Pageable pageable) {
        log.debug("Getting products by category slug: {} with pagination: {}", categorySlug, pageable);
        
        Page<Product> products = productRepository.findByCategorySlugAndStatus(
                categorySlug, ProductStatus.ACTIVE, pageable);
        return mapToPageResponse(products);
    }

    @Override
    public PageResponse<ProductSummaryDto> getProductsByBrand(String brand, Pageable pageable) {
        log.debug("Getting products by brand: {} with pagination: {}", brand, pageable);
        
        Page<Product> products = productRepository.findByBrand(brand, pageable);
        return mapToPageResponse(products);
    }

    @Override
    @Cacheable(value = "featured", key = "'all'")
    public List<ProductSummaryDto> getFeaturedProducts() {
        log.debug("Getting all featured products");
        
        List<Product> products = productRepository.findByIsFeaturedTrue();
        return products.stream()
                .map(entityMapper::toProductSummaryDto)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "featured", key = "'limit_' + #limit")
    public List<ProductSummaryDto> getFeaturedProducts(int limit) {
        log.debug("Getting featured products with limit: {}", limit);
        
        Pageable pageable = PageRequest.of(0, limit, Sort.by("createdAt").descending());
        Page<Product> products = productRepository.findByIsFeaturedTrue(pageable);
        
        return products.getContent().stream()
                .map(entityMapper::toProductSummaryDto)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "featured", key = "'category_' + #categoryId")
    public List<ProductSummaryDto> getFeaturedProductsByCategory(Long categoryId) {
        log.debug("Getting featured products by category ID: {}", categoryId);
        
        List<Product> products = productRepository.findFeaturedByCategoryId(categoryId, ProductStatus.ACTIVE);
        return products.stream()
                .map(entityMapper::toProductSummaryDto)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "featured", key = "'category_' + #categoryId + '_limit_' + #limit")
    public List<ProductSummaryDto> getFeaturedProductsByCategory(Long categoryId, int limit) {
        log.debug("Getting featured products by category ID: {} with limit: {}", categoryId, limit);
        
        List<Product> products = productRepository.findFeaturedByCategoryId(categoryId, ProductStatus.ACTIVE);
        return products.stream()
                .limit(limit)
                .map(entityMapper::toProductSummaryDto)
                .collect(Collectors.toList());
    }

    @Override
    public SearchResultDto searchProducts(ProductSearchDto searchDto) {
        log.debug("Searching products with criteria: {}", searchDto);
        
        long startTime = System.currentTimeMillis();
        
        Specification<Product> spec = ProductSpecification.buildFromSearchDto(searchDto);
        Pageable pageable = createPageable(searchDto);
        
        Page<Product> products = productRepository.findAll(spec, pageable);
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        List<ProductSummaryDto> productDtos = products.getContent().stream()
                .map(entityMapper::toProductSummaryDto)
                .collect(Collectors.toList());
        
        return SearchResultDto.builder()
                .query(searchDto.getQuery())
                .totalResults(products.getTotalElements())
                .executionTimeMs(executionTime)
                .products(productDtos)
                .metadata(createSearchMetadata(searchDto, products))
                .build();
    }

    @Override
    public PageResponse<ProductSummaryDto> searchProductsSimple(String query, Pageable pageable) {
        log.debug("Simple search for products with query: {}", query);
        
        Page<Product> products = productRepository.searchByQuery(query, ProductStatus.ACTIVE, pageable);
        return mapToPageResponse(products);
    }

    @Override
    public PageResponse<ProductSummaryDto> fullTextSearch(String query, Pageable pageable) {
        log.debug("Full-text search for products with query: {}", query);
        
        Page<Product> products = productRepository.fullTextSearch(query, ProductStatus.ACTIVE.name(), pageable);
        return mapToPageResponse(products);
    }

    // Helper methods
    private Product findProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with ID: " + id));
    }

    private PageResponse<ProductSummaryDto> mapToPageResponse(Page<Product> products) {
        List<ProductSummaryDto> content = products.getContent().stream()
                .map(entityMapper::toProductSummaryDto)
                .collect(Collectors.toList());
        
        return PageResponse.<ProductSummaryDto>builder()
                .content(content)
                .page(products.getNumber())
                .size(products.getSize())
                .totalElements(products.getTotalElements())
                .totalPages(products.getTotalPages())
                .first(products.isFirst())
                .last(products.isLast())
                .empty(products.isEmpty())
                .build();
    }

    private Pageable createPageable(ProductSearchDto searchDto) {
        Sort sort = createSort(searchDto.getEffectiveSortBy(), searchDto.getEffectiveSortDirection());
        return PageRequest.of(searchDto.getPage(), searchDto.getSize(), sort);
    }

    private Sort createSort(String sortBy, String direction) {
        Sort.Direction sortDirection = "DESC".equalsIgnoreCase(direction) ? 
                Sort.Direction.DESC : Sort.Direction.ASC;
        
        return switch (sortBy.toLowerCase()) {
            case "name" -> Sort.by(sortDirection, "name");
            case "price" -> Sort.by(sortDirection, "price");
            case "created" -> Sort.by(sortDirection, "createdAt");
            case "updated" -> Sort.by(sortDirection, "updatedAt");
            case "brand" -> Sort.by(sortDirection, "brand");
            default -> Sort.by(sortDirection, "name");
        };
    }

    private SearchResultDto.SearchMetadata createSearchMetadata(ProductSearchDto searchDto, Page<Product> products) {
        return SearchResultDto.SearchMetadata.builder()
                .searchType("specification")
                .sortBy(searchDto.getEffectiveSortBy())
                .sortDirection(searchDto.getEffectiveSortDirection())
                .page(searchDto.getPage())
                .size(searchDto.getSize())
                .hasMore(!products.isLast())
                .build();
    }

    // Validation and helper methods will be implemented in next part
    @Override
    public void validateProductData(ProductCreateDto createDto) {
        // TODO: Implement validation logic
    }

    @Override
    public void validateProductData(ProductUpdateDto updateDto, Long productId) {
        // TODO: Implement validation logic
    }

    private void createProductCategoryRelationships(Product product, List<Long> categoryIds, Long primaryCategoryId) {
        // TODO: Implement category relationship creation
    }

    private void updateProductCategoryRelationships(Product product, List<Long> categoryIds, Long primaryCategoryId) {
        // TODO: Implement category relationship updates
    }

    private void createInitialInventory(Product product, ProductCreateDto createDto) {
        // TODO: Implement initial inventory creation
    }

    private void updateProductFields(Product product, ProductUpdateDto updateDto) {
        // TODO: Implement field updates
    }

    // Placeholder implementations for remaining methods
    @Override public List<ProductSummaryDto> getRelatedProducts(Long productId) { return List.of(); }
    @Override public List<ProductSummaryDto> getRelatedProducts(Long productId, int limit) { return List.of(); }
    @Override public List<ProductSummaryDto> getProductsInSameCategory(Long productId) { return List.of(); }
    @Override public ProductResponseDto activateProduct(Long id) { return null; }
    @Override public ProductResponseDto deactivateProduct(Long id) { return null; }
    @Override public ProductResponseDto discontinueProduct(Long id) { return null; }
    @Override public List<ProductResponseDto> bulkUpdateStatus(List<Long> productIds, ProductStatus status) { return List.of(); }
    @Override public ProductResponseDto setFeatured(Long id, boolean featured) { return null; }
    @Override public List<ProductResponseDto> bulkSetFeatured(List<Long> productIds, boolean featured) { return List.of(); }
    @Override public boolean isSkuUnique(String sku) { return !productRepository.existsBySku(sku); }
    @Override public boolean isSkuUnique(String sku, Long excludeProductId) { return !productRepository.existsBySkuAndIdNot(sku, excludeProductId); }
    @Override public long getTotalProductCount() { return productRepository.count(); }
    @Override public long getActiveProductCount() { return productRepository.countByStatus(ProductStatus.ACTIVE); }
    @Override public long getFeaturedProductCount() { return productRepository.countByIsFeaturedTrue(); }
    @Override public long getProductCountByCategory(Long categoryId) { return productRepository.countByCategoryIdAndStatus(categoryId, ProductStatus.ACTIVE); }
    @Override public long getProductCountByBrand(String brand) { return productRepository.countByBrand(brand); }
    @Override public List<ProductSummaryDto> getRecentProducts() { return List.of(); }
    @Override public List<ProductSummaryDto> getRecentProducts(int limit) { return List.of(); }
    @Override public PageResponse<ProductSummaryDto> getRecentProducts(Pageable pageable) { return null; }
    @Override public PageResponse<ProductSummaryDto> getProductsOnSale(Pageable pageable) { return null; }
    @Override public List<ProductSummaryDto> getProductsOnSale(int limit) { return List.of(); }
    @Override public PageResponse<ProductSummaryDto> getInStockProducts(Pageable pageable) { return null; }
    @Override public List<ProductSummaryDto> getLowStockProducts() { return List.of(); }
    @Override public List<String> getAllBrands() { return List.of(); }
    @Override public List<String> getActiveBrands() { return List.of(); }
    @Override public PageResponse<ProductSummaryDto> getProductsByBrands(List<String> brands, Pageable pageable) { return null; }
    @Override public PageResponse<ProductSummaryDto> getProductsByCategories(List<Long> categoryIds, Pageable pageable) { return null; }
    @Override public long getProductCountByCategories(List<Long> categoryIds) { return 0; }
    @Override public PageResponse<ProductSummaryDto> getProductsByPriceRange(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) { return null; }
    @Override public boolean existsById(Long id) { return productRepository.existsById(id); }
    @Override public boolean existsBySku(String sku) { return productRepository.existsBySku(sku); }
    @Override @CacheEvict(value = "products", allEntries = true) public void clearProductCache() { }
    @Override @CacheEvict(value = "products", key = "#productId") public void clearProductCache(Long productId) { }
    @Override public void refreshProductCache(Long productId) { }
    @Override public List<ProductSummaryDto> getProductsByIds(List<Long> productIds) { return List.of(); }
    @Override public void bulkDeleteProducts(List<Long> productIds) { }
}
