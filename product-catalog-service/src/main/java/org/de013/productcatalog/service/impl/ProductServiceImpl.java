package org.de013.productcatalog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.dto.product.*;

import org.de013.productcatalog.entity.*;
import java.util.Optional;
import org.de013.productcatalog.entity.enums.ProductStatus;
import org.de013.productcatalog.exception.DuplicateSkuException;
import org.de013.productcatalog.exception.ProductNotFoundException;
import org.de013.productcatalog.repository.*;

import org.de013.productcatalog.service.ProductService;
import org.de013.productcatalog.mapper.ProductMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
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
    private final ProductMapper productMapper;

    @Override
    @Transactional
    public ProductResponseDto createProduct(ProductCreateDto createDto) {
        log.info("Creating product with SKU: {}", createDto.getSku());

        validateProductData(createDto);

        try {
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

            Product savedProduct = productRepository.save(product);
            log.debug("Product entity saved with ID: {}", savedProduct.getId());

            // Create product-category relationships
            createProductCategoryRelationships(savedProduct, createDto.getCategoryIds(), createDto.getPrimaryCategoryId());

            // Create initial inventory if specified
            if (createDto.getInitialQuantity() != null && createDto.getInitialQuantity() > 0) {
                createInitialInventory(savedProduct, createDto);
            }

            log.info("Product created successfully with ID: {}", savedProduct.getId());

            // Load the created ProductCategories and set them to the product
            List<ProductCategory> productCategories = productCategoryRepository.findByProductId(savedProduct.getId());
            savedProduct.setProductCategories(productCategories);

            return productMapper.toProductResponseDto(savedProduct);

        } catch (Exception e) {
            log.error("Error creating product with SKU: {}", createDto.getSku(), e);
            throw e; // Re-throw to trigger transaction rollback
        }
    }

    @Override
    @Transactional
    @CachePut(value = "products", key = "#id")
    @CacheEvict(value = {"featuredProducts", "popularProducts", "searchResults"}, allEntries = true)
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
        return productMapper.toProductResponseDto(product);
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
        return productMapper.toProductDetailDto(product);
    }

    @Override
    @Cacheable(value = "products", key = "#sku")
    public ProductDetailDto getProductBySku(String sku) {
        log.debug("Getting product by SKU: {}", sku);
        
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new RuntimeException("Product not found with SKU: " + sku));
        
        return productMapper.toProductDetailDto(product);
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
                categoryId, ProductStatus.ACTIVE.name(), pageable);
        return mapToPageResponse(products);
    }

    @Override
    public PageResponse<ProductSummaryDto> getProductsByCategorySlug(String categorySlug, Pageable pageable) {
        log.debug("Getting products by category slug: {} with pagination: {}", categorySlug, pageable);
        
        Page<Product> products = productRepository.findByCategorySlugAndStatus(
                categorySlug, ProductStatus.ACTIVE.name(), pageable);
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
                .map(productMapper::toProductSummaryDto)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "featured", key = "'limit_' + #limit")
    public List<ProductSummaryDto> getFeaturedProducts(int limit) {
        log.debug("Getting featured products with limit: {}", limit);
        
        Pageable pageable = PageRequest.of(0, limit, Sort.by("createdAt").descending());
        Page<Product> products = productRepository.findByIsFeaturedTrue(pageable);
        
        return products.getContent().stream()
                .map(productMapper::toProductSummaryDto)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "featured", key = "'category_' + #categoryId")
    public List<ProductSummaryDto> getFeaturedProductsByCategory(Long categoryId) {
        log.debug("Getting featured products by category ID: {}", categoryId);
        
        List<Product> products = productRepository.findFeaturedByCategoryId(categoryId, ProductStatus.ACTIVE.name());
        return products.stream()
                .map(productMapper::toProductSummaryDto)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "featured", key = "'category_' + #categoryId + '_limit_' + #limit")
    public List<ProductSummaryDto> getFeaturedProductsByCategory(Long categoryId, int limit) {
        log.debug("Getting featured products by category ID: {} with limit: {}", categoryId, limit);
        
        List<Product> products = productRepository.findFeaturedByCategoryId(categoryId, ProductStatus.ACTIVE.name());
        return products.stream()
                .limit(limit)
                .map(productMapper::toProductSummaryDto)
                .collect(Collectors.toList());
    }



    @Override
    public PageResponse<ProductSummaryDto> searchProductsSimple(String query, Pageable pageable) {
        log.debug("Simple search for products with query: {}", query);
        
        Page<Product> products = productRepository.searchByQuery(query, ProductStatus.ACTIVE.name(), pageable);
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
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private PageResponse<ProductSummaryDto> mapToPageResponse(Page<Product> products) {
        List<ProductSummaryDto> content = products.getContent().stream()
                .map(productMapper::toProductSummaryDto)
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



    // Validation methods
    @Override
    public void validateProductData(ProductCreateDto createDto) {
        log.debug("Validating product data for SKU: {}", createDto.getSku());

        // Check for duplicate SKU
        if (productRepository.existsBySku(createDto.getSku())) {
            throw new DuplicateSkuException(createDto.getSku());
        }

        // Basic price validation
        if (createDto.getPrice() != null && createDto.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Product price must be greater than zero");
        }
    }

    @Override
    public void validateProductData(ProductUpdateDto updateDto, Long productId) {
        log.debug("Validating product update data for ID: {}", productId);

        // Check for duplicate SKU (excluding current product)
        if (updateDto.getSku() != null) {
            productRepository.findBySku(updateDto.getSku())
                    .filter(product -> !product.getId().equals(productId))
                    .ifPresent(product -> {
                        throw new DuplicateSkuException(updateDto.getSku(), product.getId());
                    });
        }

        // Basic price validation
        if (updateDto.getPrice() != null && updateDto.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Product price must be greater than zero");
        }
    }

    private void createProductCategoryRelationships(Product product, List<Long> categoryIds, Long primaryCategoryId) {
        log.debug("Creating category relationships for product ID: {} with categories: {}", product.getId(), categoryIds);

        if (categoryIds == null || categoryIds.isEmpty()) {
            log.warn("No category IDs provided for product: {}", product.getSku());
            return;
        }

        // Validate all categories exist and are active
        List<Category> categories = categoryRepository.findAllById(categoryIds);

        if (categories.size() != categoryIds.size()) {
            List<Long> foundIds = categories.stream().map(Category::getId).toList();
            List<Long> missingIds = categoryIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .toList();
            throw new IllegalArgumentException("Categories not found: " + missingIds);
        }

        // Check if all categories are active
        List<Category> inactiveCategories = categories.stream()
                .filter(category -> !category.getIsActive())
                .toList();
        if (!inactiveCategories.isEmpty()) {
            List<Long> inactiveIds = inactiveCategories.stream().map(Category::getId).toList();
            throw new IllegalArgumentException("Cannot assign product to inactive categories: " + inactiveIds);
        }

        // Create ProductCategory relationships
        List<ProductCategory> productCategories = new ArrayList<>();
        for (Category category : categories) {
            boolean isPrimary = primaryCategoryId != null && primaryCategoryId.equals(category.getId());

            ProductCategory productCategory = ProductCategory.builder()
                    .product(product)
                    .category(category)
                    .isPrimary(isPrimary)
                    .build();

            productCategories.add(productCategory);
        }

        // If no primary category specified, make the first one primary
        if (primaryCategoryId == null && !productCategories.isEmpty()) {
            productCategories.get(0).setPrimary(true);
            log.debug("No primary category specified, setting first category as primary: {}",
                     productCategories.get(0).getCategory().getId());
        }

        // Save all relationships
        productCategoryRepository.saveAll(productCategories);

        log.info("Created {} category relationships for product: {}", productCategories.size(), product.getSku());
    }

    private void updateProductCategoryRelationships(Product product, List<Long> categoryIds, Long primaryCategoryId) {
        // TODO: Implement category relationship updates
    }

    private void createInitialInventory(Product product, ProductCreateDto createDto) {
        log.debug("Setting initial inventory for product ID: {} with quantity: {}",
                 product.getId(), createDto.getInitialQuantity());

        // Skip inventory setup for digital products
        if (Boolean.TRUE.equals(createDto.getIsDigital())) {
            log.debug("Skipping inventory setup for digital product: {}", product.getSku());
            return;
        }

        // Get existing inventory (created by database trigger)
        Optional<Inventory> existingInventory = inventoryRepository.findByProductId(product.getId());

        if (existingInventory.isPresent()) {
            // Update existing inventory with initial values
            Inventory inventory = existingInventory.get();

            // Set initial quantity
            if (createDto.getInitialQuantity() != null) {
                inventory.setQuantity(createDto.getInitialQuantity());
            }

            // Set min stock level and reorder point
            if (createDto.getMinStockLevel() != null) {
                inventory.setMinStockLevel(createDto.getMinStockLevel());
                inventory.setReorderPoint(createDto.getMinStockLevel());
            }

            // Set max stock level if initial quantity is provided
            if (createDto.getInitialQuantity() != null && createDto.getInitialQuantity() > 0) {
                // Set max stock level to 10x initial quantity as a reasonable default
                inventory.setMaxStockLevel(createDto.getInitialQuantity() * 10);
            }

            inventoryRepository.save(inventory);
            log.debug("Updated existing inventory for product ID: {} with quantity: {}",
                     product.getId(), inventory.getQuantity());
        } else {
            // Fallback: Create inventory if trigger didn't work for some reason
            log.warn("No inventory found for product ID: {}, creating new one", product.getId());

            Inventory inventory = Inventory.builder()
                    .product(product)
                    .quantity(createDto.getInitialQuantity() != null ? createDto.getInitialQuantity() : 0)
                    .reservedQuantity(0)
                    .minStockLevel(createDto.getMinStockLevel() != null ? createDto.getMinStockLevel() : 0)
                    .reorderPoint(createDto.getMinStockLevel() != null ? createDto.getMinStockLevel() : 0)
                    .trackInventory(true)
                    .allowBackorder(false)
                    .build();

            // Set max stock level if provided (optional)
            if (createDto.getInitialQuantity() != null && createDto.getInitialQuantity() > 0) {
                // Set max stock level to 10x initial quantity as a reasonable default
                inventory.setMaxStockLevel(createDto.getInitialQuantity() * 10);
            }

            inventoryRepository.save(inventory);

            log.info("Created initial inventory for product: {} with quantity: {}",
                    product.getSku(), inventory.getQuantity());
        }
    }

    private void updateProductFields(Product product, ProductUpdateDto updateDto) {
        // Update basic product information
        if (updateDto.getName() != null) {
            product.setName(updateDto.getName());
        }

        if (updateDto.getDescription() != null) {
            product.setDescription(updateDto.getDescription());
        }

        if (updateDto.getShortDescription() != null) {
            product.setShortDescription(updateDto.getShortDescription());
        }

        if (updateDto.getSku() != null) {
            product.setSku(updateDto.getSku());
        }

        // Update pricing information
        if (updateDto.getPrice() != null) {
            product.setPrice(updateDto.getPrice());
        }

        if (updateDto.getComparePrice() != null) {
            product.setComparePrice(updateDto.getComparePrice());
        }

        if (updateDto.getCostPrice() != null) {
            product.setCostPrice(updateDto.getCostPrice());
        }

        // Update product attributes
        if (updateDto.getBrand() != null) {
            product.setBrand(updateDto.getBrand());
        }

        if (updateDto.getWeight() != null) {
            product.setWeight(updateDto.getWeight());
        }

        if (updateDto.getDimensions() != null) {
            product.setDimensions(updateDto.getDimensions());
        }

        // Update product status and flags
        if (updateDto.getStatus() != null) {
            product.setStatus(updateDto.getStatus());
        }

        if (updateDto.getIsFeatured() != null) {
            product.setIsFeatured(updateDto.getIsFeatured());
        }

        if (updateDto.getIsDigital() != null) {
            product.setIsDigital(updateDto.getIsDigital());
        }

        if (updateDto.getRequiresShipping() != null) {
            product.setRequiresShipping(updateDto.getRequiresShipping());
        }

        // Update SEO fields
        if (updateDto.getMetaTitle() != null) {
            product.setMetaTitle(updateDto.getMetaTitle());
        }

        if (updateDto.getMetaDescription() != null) {
            product.setMetaDescription(updateDto.getMetaDescription());
        }

        if (updateDto.getSearchKeywords() != null) {
            product.setSearchKeywords(updateDto.getSearchKeywords());
        }
    }

    // Essential implementations only
    @Override public boolean isSkuUnique(String sku) { return !productRepository.existsBySku(sku); }
    @Override public boolean isSkuUnique(String sku, Long excludeProductId) { return !productRepository.existsBySkuAndIdNot(sku, excludeProductId); }
    @Override public long getTotalProductCount() { return productRepository.count(); }
    @Override public long getActiveProductCount() { return productRepository.countByStatus(ProductStatus.ACTIVE); }
    @Override public long getFeaturedProductCount() { return productRepository.countByIsFeaturedTrue(); }
    @Override public boolean existsById(Long id) { return productRepository.existsById(id); }
    @Override public boolean existsBySku(String sku) { return productRepository.existsBySku(sku); }
}
