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
import org.de013.productcatalog.dto.product.ProductVariantDto;
import org.de013.productcatalog.dto.variant.ProductVariantCreateDto;
import org.de013.productcatalog.dto.variant.ProductVariantUpdateDto;
import org.de013.productcatalog.service.ProductVariantService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Product Variants", description = "Product variant management operations")
public class ProductVariantController {

    private final ProductVariantService variantService;

    // ==================== CRUD Operations ====================

    @Operation(summary = "[ADMIN] Create product variant", description = "Create a new variant for a product")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Variant created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid variant data"),
        @ApiResponse(responseCode = "404", description = "Product not found"),
        @ApiResponse(responseCode = "409", description = "Variant combination already exists")
    })
    @PostMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.VARIANTS)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<ProductVariantDto>> createVariant(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Variant creation data", required = true)
            @Valid @RequestBody ProductVariantCreateDto createDto) {

        log.info("Creating variant for product ID: {}", id);

        ProductVariantDto variant = variantService.createVariant(id, createDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(org.de013.common.dto.ApiResponse.success(variant, "Variant created successfully"));
    }

    @Operation(summary = "[ADMIN] Update product variant", description = "Update an existing product variant")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Variant updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid variant data"),
        @ApiResponse(responseCode = "404", description = "Variant not found"),
        @ApiResponse(responseCode = "409", description = "Variant combination already exists")
    })
    @PutMapping(ApiPaths.VARIANTS + ApiPaths.VARIANT_ID_PARAM)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<ProductVariantDto>> updateVariant(
            @Parameter(description = "Variant ID", required = true)
            @PathVariable Long variantId,
            @Parameter(description = "Variant update data", required = true)
            @Valid @RequestBody ProductVariantUpdateDto updateDto) {

        log.info("Updating variant with ID: {}", variantId);

        ProductVariantDto variant = variantService.updateVariant(variantId, updateDto);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(variant, "Variant updated successfully"));
    }

    @Operation(summary = "[ADMIN] Delete product variant", description = "Delete a product variant")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Variant deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Variant not found")
    })
    @DeleteMapping(ApiPaths.VARIANTS + ApiPaths.VARIANT_ID_PARAM)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Void>> deleteVariant(
            @Parameter(description = "Variant ID", required = true)
            @PathVariable Long variantId) {

        log.info("Deleting variant with ID: {}", variantId);

        variantService.deleteVariant(variantId);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(null, "Variant deleted successfully"));
    }

    @Operation(summary = "Get product variant by ID", description = "Retrieve detailed variant information by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Variant found"),
        @ApiResponse(responseCode = "404", description = "Variant not found")
    })
    @GetMapping(ApiPaths.VARIANTS + ApiPaths.VARIANT_ID_PARAM)
    public ResponseEntity<org.de013.common.dto.ApiResponse<ProductVariantDto>> getVariantById(
            @Parameter(description = "Variant ID", required = true)
            @PathVariable Long variantId) {

        log.info("Getting variant by ID: {}", variantId);

        ProductVariantDto variant = variantService.getVariantById(variantId);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(variant));
    }

    @Operation(summary = "Get product variant by SKU", description = "Retrieve variant information by SKU")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Variant found"),
        @ApiResponse(responseCode = "404", description = "Variant not found")
    })
    @GetMapping(ApiPaths.VARIANTS + ApiPaths.VARIANT_SKU_PARAM)
    public ResponseEntity<org.de013.common.dto.ApiResponse<ProductVariantDto>> getVariantBySku(
            @Parameter(description = "Variant SKU", required = true)
            @PathVariable String sku) {

        log.info("Getting variant by SKU: {}", sku);

        ProductVariantDto variant = variantService.getVariantBySku(sku);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(variant));
    }

    @Operation(summary = "Get product variants", description = "Get all variants for a specific product")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Variants retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.VARIANTS)
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<ProductVariantDto>>> getVariantsByProductId(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id) {

        log.info("Getting variants for product ID: {}", id);

        List<ProductVariantDto> variants = variantService.getVariantsByProductId(id);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(variants));
    }
}
