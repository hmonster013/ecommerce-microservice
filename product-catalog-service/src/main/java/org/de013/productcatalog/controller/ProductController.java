package org.de013.productcatalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.de013.common.dto.ApiResponse;
import org.de013.common.dto.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Tag(name = "Product Management", description = "Product catalog management endpoints")
public class ProductController {

    @GetMapping
    @Operation(
            summary = "Get all products",
            description = "Retrieve paginated list of all products with optional filtering"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Products retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getAllProducts(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Category filter", example = "electronics")
            @RequestParam(required = false) String category,
            @Parameter(description = "Search keyword", example = "laptop")
            @RequestParam(required = false) String search) {
        
        // Mock response for demo
        ProductResponse product = ProductResponse.builder()
                .id(1L)
                .name("Sample Product")
                .description("This is a sample product for demo")
                .price(BigDecimal.valueOf(99.99))
                .category("Electronics")
                .stock(100)
                .createdAt(LocalDateTime.now())
                .build();
        
        PageResponse<ProductResponse> pageResponse = PageResponse.<ProductResponse>builder()
                .content(List.of(product))
                .page(page)
                .size(size)
                .totalElements(1L)
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(false)
                .build();
        
        return ResponseEntity.ok(ApiResponse.success(pageResponse));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get product by ID",
            description = "Retrieve product details by product ID"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Product found",
                    content = @Content(schema = @Schema(implementation = ProductResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Product not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id) {
        
        // Mock response for demo
        ProductResponse product = ProductResponse.builder()
                .id(id)
                .name("Sample Product " + id)
                .description("This is a sample product for demo")
                .price(BigDecimal.valueOf(99.99))
                .category("Electronics")
                .stock(100)
                .createdAt(LocalDateTime.now())
                .build();
        
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    // Inner class for demo - normally this would be in dto package
    @Schema(description = "Product response object")
    public static class ProductResponse {
        @Schema(description = "Product ID", example = "1")
        private Long id;
        
        @Schema(description = "Product name", example = "Laptop")
        private String name;
        
        @Schema(description = "Product description", example = "High-performance laptop")
        private String description;
        
        @Schema(description = "Product price", example = "999.99")
        private BigDecimal price;
        
        @Schema(description = "Product category", example = "Electronics")
        private String category;
        
        @Schema(description = "Stock quantity", example = "50")
        private Integer stock;
        
        @Schema(description = "Creation timestamp")
        private LocalDateTime createdAt;

        // Builder pattern
        public static ProductResponseBuilder builder() {
            return new ProductResponseBuilder();
        }

        public static class ProductResponseBuilder {
            private Long id;
            private String name;
            private String description;
            private BigDecimal price;
            private String category;
            private Integer stock;
            private LocalDateTime createdAt;

            public ProductResponseBuilder id(Long id) {
                this.id = id;
                return this;
            }

            public ProductResponseBuilder name(String name) {
                this.name = name;
                return this;
            }

            public ProductResponseBuilder description(String description) {
                this.description = description;
                return this;
            }

            public ProductResponseBuilder price(BigDecimal price) {
                this.price = price;
                return this;
            }

            public ProductResponseBuilder category(String category) {
                this.category = category;
                return this;
            }

            public ProductResponseBuilder stock(Integer stock) {
                this.stock = stock;
                return this;
            }

            public ProductResponseBuilder createdAt(LocalDateTime createdAt) {
                this.createdAt = createdAt;
                return this;
            }

            public ProductResponse build() {
                ProductResponse response = new ProductResponse();
                response.id = this.id;
                response.name = this.name;
                response.description = this.description;
                response.price = this.price;
                response.category = this.category;
                response.stock = this.stock;
                response.createdAt = this.createdAt;
                return response;
            }
        }

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        
        public Integer getStock() { return stock; }
        public void setStock(Integer stock) { this.stock = stock; }
        
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
}
