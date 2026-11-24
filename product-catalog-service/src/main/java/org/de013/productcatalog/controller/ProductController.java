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
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.controller.BaseController;
import org.de013.common.dto.ApiResult;
import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.dto.product.ProductCreateDto;
import org.de013.productcatalog.dto.product.ProductDetailDto;
import org.de013.productcatalog.dto.product.ProductResponseDto;
import org.de013.productcatalog.dto.product.ProductSummaryDto;
import org.de013.productcatalog.dto.product.ProductUpdateDto;
import org.de013.productcatalog.entity.enums.ProductStatus;
import org.de013.productcatalog.service.ProductService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Validated
@Tag(name = "Products", description = "Product management API")
public class ProductController extends BaseController {

    private final ProductService productService;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "name", "price", "createdAt", "updatedAt", "brand", "status", "sku"
    );

    private Pageable sanitizePageable(Pageable pageable) {
        Sort sort = Sort.by(Sort.Direction.ASC, "name");
        if (pageable.getSort().isSorted()) {
            List<Sort.Order> orders = new ArrayList<>();
            for (Sort.Order order : pageable.getSort()) {
                if (ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                    orders.add(order);
                }
            }
            if (!orders.isEmpty()) {
                sort = Sort.by(orders);
            }
        }
        sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    @Operation(summary = "Get all products", description = "Retrieve paginated list of products with optional filtering and sorting")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Products retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    })
    @GetMapping
    public ResponseEntity<ApiResult<PageResponse<ProductSummaryDto>>> getAllProducts(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable,

            @Parameter(description = "Filter by product status")
            @RequestParam(required = false) ProductStatus status,

            @Parameter(description = "Filter by brand")
            @RequestParam(required = false) String brand,

            @Parameter(description = "Filter by category ID")
            @RequestParam(required = false) Long categoryId,

            @Parameter(description = "Search query for name, description, SKU, or brand")
            @RequestParam(required = false) String q) {

        log.info("Getting products with filters - status: {}, brand: {}, categoryId: {}, q: {}",
                status, brand, categoryId, q);

        Pageable sanitized = sanitizePageable(pageable);
        PageResponse<ProductSummaryDto> products = productService.getProducts(status, brand, categoryId, q, sanitized);

        return ok(products);
    }

    @Operation(summary = "Get product by ID", description = "Retrieve detailed product information by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResult<ProductDetailDto>> getProductById(
            @Parameter(description = "Product ID", required = true)
            @PathVariable @Positive(message = "Product ID must be positive") Long id) {

        log.info("Getting product by ID: {}", id);

        ProductDetailDto product = productService.getProductById(id);
        return ok(product);
    }

    @Operation(summary = "Get product by SKU", description = "Retrieve detailed product information by SKU")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping("/sku/{sku}")
    public ResponseEntity<ApiResult<ProductDetailDto>> getProductBySku(
            @Parameter(description = "Product SKU", required = true)
            @PathVariable String sku) {

        log.info("Getting product by SKU: {}", sku);

        ProductDetailDto product = productService.getProductBySku(sku);
        return ok(product);
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
    public ResponseEntity<ApiResult<ProductResponseDto>> createProduct(
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
        return created(product, "Product created successfully");
    }

    @Operation(summary = "[ADMIN] Update product", description = "Update existing product")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResult<ProductResponseDto>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateDto updateDto) {

        log.info("Updating product with ID: {}", id);
        ProductResponseDto product = productService.updateProduct(id, updateDto);
        return updated(product, "Product updated successfully");
    }

    @Operation(summary = "[ADMIN] Delete product", description = "Delete product")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResult<String>> deleteProduct(@PathVariable Long id) {
        log.info("Deleting product with ID: {}", id);
        productService.deleteProduct(id);
        return deleted("Product deleted successfully");
    }

    @Operation(summary = "Get featured products")
    @GetMapping("/featured")
    public ResponseEntity<ApiResult<List<ProductSummaryDto>>> getFeaturedProducts(
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            @RequestParam(required = false) Long categoryId) {

        log.info("Getting featured products with limit: {}, categoryId: {}", limit, categoryId);
        List<ProductSummaryDto> products = categoryId != null ?
                productService.getFeaturedProductsByCategory(categoryId, limit) :
                productService.getFeaturedProducts(limit);
        return ok(products);
    }


    @Operation(summary = "Simple product search")
    @GetMapping("/search")
    public ResponseEntity<ApiResult<PageResponse<ProductSummaryDto>>> simpleSearch(
            @RequestParam String q,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        log.info("Simple search for query: {}", q);
        PageResponse<ProductSummaryDto> results = productService.searchProductsSimple(q, pageable);
        return ok(results);
    }
}
