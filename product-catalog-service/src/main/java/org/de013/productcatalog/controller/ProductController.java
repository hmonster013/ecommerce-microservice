package org.de013.productcatalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.constant.ApiPaths;

import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.dto.product.*;
import org.de013.productcatalog.dto.search.ProductSearchDto;
import org.de013.productcatalog.dto.search.SearchResultDto;
import org.de013.productcatalog.entity.enums.ProductStatus;
import org.de013.productcatalog.service.ProductService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping(ApiPaths.API + ApiPaths.V1 + ApiPaths.PRODUCTS)
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product management API")
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "Get all products", description = "Retrieve paginated list of products with optional filtering and sorting")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Products retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    })
    @GetMapping
    public ResponseEntity<org.de013.common.dto.ApiResponse<PageResponse<ProductSummaryDto>>> getAllProducts(
            @Parameter(description = "Pagination and sorting parameters")
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable,

            @Parameter(description = "Filter by product status")
            @RequestParam(required = false) ProductStatus status,

            @Parameter(description = "Filter by brand")
            @RequestParam(required = false) String brand,

            @Parameter(description = "Filter by category ID")
            @RequestParam(required = false) Long categoryId) {

        log.info("Getting products with filters - status: {}, brand: {}, categoryId: {}",
                status, brand, categoryId);

        PageResponse<ProductSummaryDto> products;

        if (status != null) {
            products = productService.getProductsByStatus(status, pageable);
        } else if (brand != null) {
            products = productService.getProductsByBrand(brand, pageable);
        } else if (categoryId != null) {
            products = productService.getProductsByCategory(categoryId, pageable);
        } else {
            products = productService.getAllProducts(pageable);
        }

        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(products));
    }

    @Operation(summary = "Get product by ID", description = "Retrieve detailed product information by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Product found"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping(ApiPaths.ID_PARAM)
    public ResponseEntity<org.de013.common.dto.ApiResponse<ProductDetailDto>> getProductById(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id) {

        log.info("Getting product by ID: {}", id);

        ProductDetailDto product = productService.getProductById(id);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(product));
    }

    @Operation(summary = "Get product by SKU", description = "Retrieve detailed product information by SKU")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Product found"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping("/sku/{sku}")
    public ResponseEntity<org.de013.common.dto.ApiResponse<ProductDetailDto>> getProductBySku(
            @Parameter(description = "Product SKU", required = true)
            @PathVariable String sku) {

        log.info("Getting product by SKU: {}", sku);

        ProductDetailDto product = productService.getProductBySku(sku);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(product));
    }

    @Operation(summary = "Create new product", description = "Create a new product (Admin only)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Product created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid product data"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<ProductResponseDto>> createProduct(
            @Parameter(description = "Product creation data", required = true)
            @Valid @RequestBody ProductCreateDto createDto) {

        log.info("Creating new product with SKU: {}", createDto.getSku());

        ProductResponseDto product = productService.createProduct(createDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(org.de013.common.dto.ApiResponse.success(product, "Product created successfully"));
    }

    @Operation(summary = "Update product", description = "Update existing product (Admin only)")
    @PutMapping(ApiPaths.ID_PARAM)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<ProductResponseDto>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateDto updateDto) {

        log.info("Updating product with ID: {}", id);
        ProductResponseDto product = productService.updateProduct(id, updateDto);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(product, "Product updated successfully"));
    }

    @Operation(summary = "Delete product", description = "Delete product (Admin only)")
    @DeleteMapping(ApiPaths.ID_PARAM)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        log.info("Deleting product with ID: {}", id);
        productService.deleteProduct(id);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(null, "Product deleted successfully"));
    }

    @Operation(summary = "Get featured products")
    @GetMapping("/featured")
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<ProductSummaryDto>>> getFeaturedProducts(
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            @RequestParam(required = false) Long categoryId) {

        log.info("Getting featured products with limit: {}, categoryId: {}", limit, categoryId);
        List<ProductSummaryDto> products = categoryId != null ?
            productService.getFeaturedProductsByCategory(categoryId, limit) :
            productService.getFeaturedProducts(limit);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(products));
    }

    @Operation(summary = "Search products")
    @PostMapping("/search")
    public ResponseEntity<org.de013.common.dto.ApiResponse<SearchResultDto>> searchProducts(
            @Valid @RequestBody ProductSearchDto searchDto) {
        log.info("Searching products with criteria: {}", searchDto);
        SearchResultDto results = productService.searchProducts(searchDto);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(results));
    }

    @Operation(summary = "Simple product search")
    @GetMapping("/search")
    public ResponseEntity<org.de013.common.dto.ApiResponse<PageResponse<ProductSummaryDto>>> simpleSearch(
            @RequestParam String q,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        log.info("Simple search for query: {}", q);
        PageResponse<ProductSummaryDto> results = productService.searchProductsSimple(q, pageable);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(results));
    }
}
