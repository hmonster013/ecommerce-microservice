package org.de013.productcatalog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.dto.PageResponse;
import org.de013.common.exception.ResourceNotFoundException;
import org.de013.productcatalog.dto.product.ProductVariantDto;
import org.de013.productcatalog.dto.product.ProductVariantGroupDto;
import org.de013.productcatalog.dto.variant.ProductVariantCreateDto;
import org.de013.productcatalog.dto.variant.ProductVariantUpdateDto;
import org.de013.productcatalog.entity.Product;
import org.de013.productcatalog.entity.ProductVariant;
import org.de013.productcatalog.entity.enums.VariantType;
import org.de013.productcatalog.mapper.ProductVariantMapper;
import org.de013.productcatalog.repository.ProductRepository;
import org.de013.productcatalog.repository.ProductVariantRepository;
import org.de013.productcatalog.service.ProductVariantService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductVariantServiceImpl implements ProductVariantService {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final ProductVariantMapper variantMapper;

    @Override
    @Transactional
    public ProductVariantDto createVariant(Long productId, ProductVariantCreateDto createDto) {
        log.info("Creating variant for product ID: {}, type: {}, value: {}",
                productId, createDto.variantType(), createDto.value());

        validateVariantData(createDto);

        // Find product
        Product product = findProductById(productId);

        // Check for duplicate variant combination
        if (!isVariantCombinationUnique(productId, createDto.variantType(), createDto.value())) {
            throw new IllegalArgumentException(
                    String.format("Variant combination already exists: %s = %s",
                            createDto.variantType(), createDto.value()));
        }

        // Check SKU uniqueness if provided
        if (StringUtils.hasText(createDto.sku()) && !isSkuUnique(createDto.sku())) {
            throw new IllegalArgumentException("Variant SKU already exists: " + createDto.sku());
        }

        // Create variant entity
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .variantType(createDto.variantType())
                .name(createDto.name())
                .value(createDto.value())
                .priceAdjustment(createDto.priceAdjustment())
                .sku(createDto.sku())
                .displayOrder(createDto.displayOrder())
                .isActive(createDto.isActive())
                .imageUrl(createDto.imageUrl())
                .description(createDto.description())
                .build();

        ProductVariant savedVariant = variantRepository.save(variant);
        log.info("Variant created successfully with ID: {}", savedVariant.getId());

        return variantMapper.toProductVariantDto(savedVariant);
    }

    @Override
    @Transactional
    @CacheEvict(value = "variants", key = "#variantId")
    public ProductVariantDto updateVariant(Long variantId, ProductVariantUpdateDto updateDto) {
        log.info("Updating variant with ID: {}", variantId);

        validateVariantData(updateDto, variantId);

        ProductVariant variant = findVariantById(variantId);

        // Update fields if provided
        if (updateDto.variantType() != null) {
            // Check for duplicate variant combination if type or value changed
            String newValue = updateDto.value() != null ? updateDto.value() : variant.getValue();
            if (!isVariantCombinationUnique(variant.getProduct().getId(),
                    updateDto.variantType(), newValue, variantId)) {
                throw new IllegalArgumentException(
                        String.format("Variant combination already exists: %s = %s",
                                updateDto.variantType(), newValue));
            }
            variant.setVariantType(updateDto.variantType());
        }

        if (StringUtils.hasText(updateDto.name())) {
            variant.setName(updateDto.name());
        }

        if (StringUtils.hasText(updateDto.value())) {
            // Check for duplicate variant combination if value changed
            VariantType currentType = updateDto.variantType() != null ?
                    updateDto.variantType() : variant.getVariantType();
            if (!isVariantCombinationUnique(variant.getProduct().getId(),
                    currentType, updateDto.value(), variantId)) {
                throw new IllegalArgumentException(
                        String.format("Variant combination already exists: %s = %s",
                                currentType, updateDto.value()));
            }
            variant.setValue(updateDto.value());
        }

        if (updateDto.priceAdjustment() != null) {
            variant.setPriceAdjustment(updateDto.priceAdjustment());
        }

        if (StringUtils.hasText(updateDto.sku())) {
            if (!isSkuUnique(updateDto.sku(), variantId)) {
                throw new IllegalArgumentException("Variant SKU already exists: " + updateDto.sku());
            }
            variant.setSku(updateDto.sku());
        }

        if (updateDto.displayOrder() != null) {
            variant.setDisplayOrder(updateDto.displayOrder());
        }

        if (updateDto.isActive() != null) {
            variant.setIsActive(updateDto.isActive());
        }

        if (updateDto.imageUrl() != null) {
            variant.setImageUrl(updateDto.imageUrl());
        }

        if (updateDto.description() != null) {
            variant.setDescription(updateDto.description());
        }

        ProductVariant savedVariant = variantRepository.save(variant);
        log.info("Variant updated successfully with ID: {}", savedVariant.getId());

        return variantMapper.toProductVariantDto(savedVariant);
    }

    @Override
    @Transactional
    @CacheEvict(value = "variants", key = "#variantId")
    public void deleteVariant(Long variantId) {
        log.info("Deleting variant with ID: {}", variantId);

        ProductVariant variant = findVariantById(variantId);
        Long productId = variant.getProduct().getId();
        Integer deletedDisplayOrder = variant.getDisplayOrder();
        VariantType variantType = variant.getVariantType();

        // Delete the variant first
        variantRepository.delete(variant);
        log.debug("Variant deleted from database with ID: {}", variantId);

        // Reorder remaining variants to fill the gap
        if (deletedDisplayOrder != null) {
            reorderVariantsAfterDeletion(productId, variantType, deletedDisplayOrder);
        }

        log.info("Variant deleted successfully with ID: {} and display order reordered", variantId);
    }

    /**
     * Reorder variants after deletion to fill the gap in display order
     * This method updates display order for variants that come after the deleted variant
     */
    private void reorderVariantsAfterDeletion(Long productId, VariantType variantType, Integer deletedDisplayOrder) {
        log.debug("Reordering variants after deletion for product: {}, type: {}, deleted order: {}",
                productId, variantType, deletedDisplayOrder);

        // Find all variants with display order greater than the deleted variant
        // We can choose to reorder by variant type or globally for the product
        // For better organization, let's reorder by variant type
        List<ProductVariant> variantsToReorder = variantRepository
                .findVariantsToReorderAfterDeletionByType(productId, variantType, deletedDisplayOrder);

        if (!variantsToReorder.isEmpty()) {
            // Update display order for each variant (shift down by 1)
            for (ProductVariant variant : variantsToReorder) {
                variant.setDisplayOrder(variant.getDisplayOrder() - 1);
            }

            // Batch save all updated variants
            variantRepository.saveAll(variantsToReorder);

            log.debug("Reordered {} variants after deletion", variantsToReorder.size());
        }
    }

    /**
     * Reorder variants after bulk deletion to fill gaps in display order
     * This method rebuilds the display order sequence for the remaining variants
     */
    private void reorderVariantsAfterBulkDeletion(Long productId, VariantType variantType, List<ProductVariant> deletedVariants) {
        log.debug("Reordering variants after bulk deletion for product: {}, type: {}, deleted count: {}",
                productId, variantType, deletedVariants.size());

        // Get all remaining variants of the same type, ordered by display order
        List<ProductVariant> remainingVariants = variantRepository
                .findByProductIdAndVariantTypeAndIsActiveTrueOrderByDisplayOrderAsc(productId, variantType);

        if (!remainingVariants.isEmpty()) {
            // Rebuild display order sequence starting from 1
            for (int i = 0; i < remainingVariants.size(); i++) {
                remainingVariants.get(i).setDisplayOrder(i + 1);
            }

            // Batch save all updated variants
            variantRepository.saveAll(remainingVariants);

            log.debug("Rebuilt display order for {} remaining variants", remainingVariants.size());
        }
    }

    @Override
    @Cacheable(value = "variants", key = "#variantId")
    public ProductVariantDto getVariantById(Long variantId) {
        log.debug("Getting variant by ID: {}", variantId);

        ProductVariant variant = findVariantById(variantId);
        return variantMapper.toProductVariantDto(variant);
    }

    @Override
    public ProductVariantDto getVariantBySku(String sku) {
        log.debug("Getting variant by SKU: {}", sku);

        ProductVariant variant = variantRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found with SKU: " + sku));

        return variantMapper.toProductVariantDto(variant);
    }

    @Override
    public List<ProductVariantDto> getVariantsByProductId(Long productId) {
        log.debug("Getting variants for product ID: {}", productId);

        List<ProductVariant> variants = variantRepository.findByProductId(productId);
        return variantMapper.toProductVariantDtos(variants);
    }

    @Override
    public List<ProductVariantDto> getActiveVariantsByProductId(Long productId) {
        log.debug("Getting active variants for product ID: {}", productId);

        List<ProductVariant> variants = variantRepository.findByProductIdAndIsActiveTrueOrderByDisplayOrderAsc(productId);
        return variantMapper.toProductVariantDtos(variants);
    }

    @Override
    public List<ProductVariantGroupDto> getVariantGroupsByProductId(Long productId) {
        log.debug("Getting variant groups for product ID: {}", productId);

        List<ProductVariant> variants = variantRepository.findByProductIdGroupedByType(productId);
        return variantMapper.toProductVariantGroupDtos(variants);
    }

    @Override
    public PageResponse<ProductVariantDto> getVariantsByProductId(Long productId, Pageable pageable) {
        log.debug("Getting variants for product ID: {} with pagination", productId);

        Page<ProductVariant> variantPage = variantRepository.findByProductId(productId, pageable);
        List<ProductVariantDto> variantDtos = variantMapper.toProductVariantDtos(variantPage.getContent());

        return PageResponse.<ProductVariantDto>builder()
                .content(variantDtos)
                .page(variantPage.getNumber())
                .size(variantPage.getSize())
                .totalElements(variantPage.getTotalElements())
                .totalPages(variantPage.getTotalPages())
                .first(variantPage.isFirst())
                .last(variantPage.isLast())
                .build();
    }

    // Helper methods
    private Product findProductById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));
    }

    private ProductVariant findVariantById(Long variantId) {
        return variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found with ID: " + variantId));
    }

    @Override
    public void validateVariantData(ProductVariantCreateDto createDto) {
        if (createDto == null) {
            throw new IllegalArgumentException("Variant data cannot be null");
        }
        // Product ID validation removed - now handled via path parameter
    }

    @Override
    public void validateVariantData(ProductVariantUpdateDto updateDto, Long variantId) {
        if (updateDto == null) {
            throw new IllegalArgumentException("Variant update data cannot be null");
        }

        if (!updateDto.hasUpdates()) {
            throw new IllegalArgumentException("At least one field must be provided for update");
        }

        if (!variantRepository.existsById(variantId)) {
            throw new ResourceNotFoundException("Variant not found with ID: " + variantId);
        }
    }

    @Override
    public boolean isSkuUnique(String sku) {
        if (!StringUtils.hasText(sku)) {
            return true;
        }
        return !variantRepository.findBySku(sku).isPresent();
    }

    @Override
    public boolean isSkuUnique(String sku, Long excludeVariantId) {
        if (!StringUtils.hasText(sku)) {
            return true;
        }
        return variantRepository.findBySku(sku)
                .map(variant -> variant.getId().equals(excludeVariantId))
                .orElse(true);
    }

    @Override
    public boolean isVariantCombinationUnique(Long productId, VariantType variantType, String value) {
        return !variantRepository.findByProductIdAndVariantTypeAndValue(productId, variantType, value).isPresent();
    }

    @Override
    public boolean isVariantCombinationUnique(Long productId, VariantType variantType, String value, Long excludeVariantId) {
        return variantRepository.findByProductIdAndVariantTypeAndValue(productId, variantType, value)
                .map(variant -> variant.getId().equals(excludeVariantId))
                .orElse(true);
    }

    @Override
    @Transactional
    public void deleteVariants(List<Long> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return;
        }

        log.info("Bulk deleting {} variants", variantIds.size());

        // Get all variants to be deleted (to collect reordering info)
        List<ProductVariant> variantsToDelete = variantRepository.findAllById(variantIds);

        if (variantsToDelete.isEmpty()) {
            log.warn("No variants found for deletion with IDs: {}", variantIds);
            return;
        }

        // Group variants by product and variant type for efficient reordering
        Map<Long, Map<VariantType, List<ProductVariant>>> groupedVariants = variantsToDelete.stream()
                .collect(Collectors.groupingBy(
                        v -> v.getProduct().getId(),
                        Collectors.groupingBy(ProductVariant::getVariantType)
                ));

        // Delete all variants first
        variantRepository.deleteAllById(variantIds);
        log.debug("Deleted {} variants from database", variantsToDelete.size());

        // Reorder remaining variants for each product and variant type
        for (Map.Entry<Long, Map<VariantType, List<ProductVariant>>> productEntry : groupedVariants.entrySet()) {
            Long productId = productEntry.getKey();

            for (Map.Entry<VariantType, List<ProductVariant>> typeEntry : productEntry.getValue().entrySet()) {
                VariantType variantType = typeEntry.getKey();
                List<ProductVariant> deletedVariants = typeEntry.getValue();

                // Find the minimum display order among deleted variants
                Integer minDeletedOrder = deletedVariants.stream()
                        .map(ProductVariant::getDisplayOrder)
                        .filter(Objects::nonNull)
                        .min(Integer::compareTo)
                        .orElse(null);

                if (minDeletedOrder != null) {
                    reorderVariantsAfterBulkDeletion(productId, variantType, deletedVariants);
                }
            }
        }

        log.info("Bulk deleted {} variants and reordered display orders", variantsToDelete.size());
    }
}
