package org.de013.productcatalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.constant.ApiPaths;
import org.springframework.security.access.prepost.PreAuthorize;

import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.dto.product.*;

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
@RequestMapping(ApiPaths.PRODUCTS)
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
    @GetMapping(ApiPaths.SKU_PARAM)
    public ResponseEntity<org.de013.common.dto.ApiResponse<ProductDetailDto>> getProductBySku(
            @Parameter(description = "Product SKU", required = true)
            @PathVariable String sku) {

        log.info("Getting product by SKU: {}", sku);

        ProductDetailDto product = productService.getProductBySku(sku);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(product));
    }

    @Operation(
            summary = "[ADMIN] Create new product",
            description = "Create a new product in the catalog")
    @ApiResponses(value = {
        @ApiResponse(
                responseCode = "201",
                description = "Product created successfully",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ProductResponseDto.class),
                        examples = @ExampleObject(
                                name = "Product Created",
                                value = """
                                        {
                                          "success": true,
                                          "message": "Product created successfully",
                                          "data": {
                                            "id": 1,
                                            "name": "Premium Wireless Headphones",
                                            "description": "High-quality wireless headphones with noise cancellation",
                                            "sku": "WH-001",
                                            "price": 299.99,
                                            "brand": "TechBrand",
                                            "status": "ACTIVE",
                                            "featured": false,
                                            "categories": [
                                              {
                                                "id": 1,
                                                "name": "Electronics",
                                                "slug": "electronics"
                                              }
                                            ],
                                            "createdAt": "2024-01-15T10:30:00Z",
                                            "updatedAt": "2024-01-15T10:30:00Z"
                                          }
                                        }
                                        """))),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid product data - validation errors",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(
                                name = "Validation Error",
                                value = """
                                        {
                                          "success": false,
                                          "message": "Validation failed",
                                          "errors": [
                                            {
                                              "field": "sku",
                                              "message": "SKU already exists"
                                            },
                                            {
                                              "field": "price",
                                              "message": "Price must be positive"
                                            }
                                          ]
                                        }
                                        """))),
        @ApiResponse(
                responseCode = "403",
                description = "Access denied - Admin role required",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(
                                name = "Access Denied",
                                value = """
                                        {
                                          "success": false,
                                          "message": "Access denied",
                                          "error": "Insufficient privileges"
                                        }
                                        """)))
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<ProductResponseDto>> createProduct(
            @Parameter(
                    description = "Product creation data with all required fields",
                    required = true,
                    example = """
                            {
                              "name": "Premium Wireless Headphones",
                              "description": "High-quality wireless headphones with active noise cancellation and 30-hour battery life",
                              "sku": "WH-001",
                              "price": 299.99,
                              "brand": "TechBrand",
                              "categoryIds": [1, 2],
                              "featured": false,
                              "tags": ["wireless", "noise-cancellation", "premium"],
                              "specifications": {
                                "battery_life": "30 hours",
                                "connectivity": "Bluetooth 5.0",
                                "weight": "250g"
                              }
                            }
                            """)
            @Valid @RequestBody ProductCreateDto createDto) {

        log.info("Creating new product with SKU: {}", createDto.getSku());

        ProductResponseDto product = productService.createProduct(createDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(org.de013.common.dto.ApiResponse.success(product, "Product created successfully"));
    }

    @Operation(summary = "[ADMIN] Update product", description = "Update existing product")
    @PutMapping(ApiPaths.ID_PARAM)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<ProductResponseDto>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateDto updateDto) {

        log.info("Updating product with ID: {}", id);
        ProductResponseDto product = productService.updateProduct(id, updateDto);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(product, "Product updated successfully"));
    }

    @Operation(summary = "[ADMIN] Delete product", description = "Delete product")
    @DeleteMapping(ApiPaths.ID_PARAM)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        log.info("Deleting product with ID: {}", id);
        productService.deleteProduct(id);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(null, "Product deleted successfully"));
    }

    @Operation(summary = "Get featured products")
    @GetMapping(ApiPaths.FEATURED)
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<ProductSummaryDto>>> getFeaturedProducts(
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            @RequestParam(required = false) Long categoryId) {

        log.info("Getting featured products with limit: {}, categoryId: {}", limit, categoryId);
        List<ProductSummaryDto> products = categoryId != null ?
            productService.getFeaturedProductsByCategory(categoryId, limit) :
            productService.getFeaturedProducts(limit);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(products));
    }



    @Operation(summary = "Simple product search")
    @GetMapping(ApiPaths.SEARCH)
    public ResponseEntity<org.de013.common.dto.ApiResponse<PageResponse<ProductSummaryDto>>> simpleSearch(
            @RequestParam String q,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        log.info("Simple search for query: {}", q);
        PageResponse<ProductSummaryDto> results = productService.searchProductsSimple(q, pageable);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(results));
    }
}
