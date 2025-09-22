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
import org.de013.productcatalog.dto.image.ProductImageCreateDto;
import org.de013.productcatalog.dto.image.ProductImageUpdateDto;
import org.de013.productcatalog.dto.product.ProductImageDto;

import org.de013.productcatalog.service.ProductImageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Product Images", description = "Product image management operations")
public class ProductImageController {

    private final ProductImageService imageService;

    // ==================== CRUD Operations ====================

    @Operation(summary = "[ADMIN] Create product image", description = "Create a new image for a product")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Image created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid image data"),
        @ApiResponse(responseCode = "404", description = "Product not found"),
        @ApiResponse(responseCode = "409", description = "Main image already exists")
    })
    @PostMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.IMAGES)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<ProductImageDto>> createImage(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Image creation data", required = true)
            @Valid @RequestBody ProductImageCreateDto createDto) {

        log.info("Creating image for product ID: {}", id);

        ProductImageDto image = imageService.createImage(id, createDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(org.de013.common.dto.ApiResponse.success(image, "Image created successfully"));
    }

    @Operation(summary = "[ADMIN] Update product image", description = "Update an existing product image")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Image updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid image data"),
        @ApiResponse(responseCode = "404", description = "Image not found"),
        @ApiResponse(responseCode = "409", description = "Main image already exists")
    })
    @PutMapping(ApiPaths.IMAGES + ApiPaths.IMAGE_ID_PARAM)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<ProductImageDto>> updateImage(
            @Parameter(description = "Image ID", required = true)
            @PathVariable Long imageId,
            @Parameter(description = "Image update data", required = true)
            @Valid @RequestBody ProductImageUpdateDto updateDto) {

        log.info("Updating image with ID: {}", imageId);

        ProductImageDto image = imageService.updateImage(imageId, updateDto);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(image, "Image updated successfully"));
    }

    @Operation(summary = "[ADMIN] Delete product image", description = "Delete a product image")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Image deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Image not found")
    })
    @DeleteMapping(ApiPaths.IMAGES + ApiPaths.IMAGE_ID_PARAM)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Void>> deleteImage(
            @Parameter(description = "Image ID", required = true)
            @PathVariable Long imageId) {

        log.info("Deleting image with ID: {}", imageId);

        imageService.deleteImage(imageId);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(null, "Image deleted successfully"));
    }

    @Operation(summary = "Get product image by ID", description = "Retrieve detailed image information by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Image found"),
        @ApiResponse(responseCode = "404", description = "Image not found")
    })
    @GetMapping(ApiPaths.IMAGES + ApiPaths.IMAGE_ID_PARAM)
    public ResponseEntity<org.de013.common.dto.ApiResponse<ProductImageDto>> getImageById(
            @Parameter(description = "Image ID", required = true)
            @PathVariable Long imageId) {

        log.info("Getting image by ID: {}", imageId);

        ProductImageDto image = imageService.getImageById(imageId);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(image));
    }

    @Operation(summary = "Get product images", description = "Get all images for a specific product")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Images retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping(ApiPaths.PRODUCTS + ApiPaths.ID_PARAM + ApiPaths.IMAGES)
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<ProductImageDto>>> getImagesByProductId(
            @Parameter(description = "Product ID", required = true)
            @PathVariable Long id) {

        log.info("Getting images for product ID: {}", id);

        List<ProductImageDto> images = imageService.getImagesByProductId(id);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(images));
    }
}

