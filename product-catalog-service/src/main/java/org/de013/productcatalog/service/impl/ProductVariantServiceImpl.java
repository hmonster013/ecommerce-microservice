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
                productId, createDto.getVariantType(), createDto.getValue());

        validateVariantData(createDto);

        // Find product
        Product product = findProductById(productId);

        // Check for duplicate variant combination
        if (!isVariantCombinationUnique(productId, createDto.getVariantType(), createDto.getValue())) {
            throw new IllegalArgumentException(
                String.format("Variant combination already exists: %s = %s", 
                    createDto.getVariantType(), createDto.getValue()));
        }

        // Check SKU uniqueness if provided
        if (StringUtils.hasText(createDto.getSku()) && !isSkuUnique(createDto.getSku())) {
            throw new IllegalArgumentException("Variant SKU already exists: " + createDto.getSku());
        }

        // Create variant entity
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .variantType(createDto.getVariantType())
                .name(createDto.getName())
                .value(createDto.getValue())
                .priceAdjustment(createDto.getPriceAdjustment())
                .sku(createDto.getSku())
                .displayOrder(createDto.getDisplayOrder())
                .isActive(createDto.getIsActive())
                .imageUrl(createDto.getImageUrl())
                .description(createDto.getDescription())
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
        if (updateDto.getVariantType() != null) {
            // Check for duplicate variant combination if type or value changed
            String newValue = updateDto.getValue() != null ? updateDto.getValue() : variant.getValue();
            if (!isVariantCombinationUnique(variant.getProduct().getId(), 
                    updateDto.getVariantType(), newValue, variantId)) {
                throw new IllegalArgumentException(
                    String.format("Variant combination already exists: %s = %s", 
                        updateDto.getVariantType(), newValue));
            }
            variant.setVariantType(updateDto.getVariantType());
        }

        if (StringUtils.hasText(updateDto.getName())) {
            variant.setName(updateDto.getName());
        }

        if (StringUtils.hasText(updateDto.getValue())) {
            // Check for duplicate variant combination if value changed
            VariantType currentType = updateDto.getVariantType() != null ? 
                updateDto.getVariantType() : variant.getVariantType();
            if (!isVariantCombinationUnique(variant.getProduct().getId(), 
                    currentType, updateDto.getValue(), variantId)) {
                throw new IllegalArgumentException(
                    String.format("Variant combination already exists: %s = %s", 
                        currentType, updateDto.getValue()));
            }
            variant.setValue(updateDto.getValue());
        }

        if (updateDto.getPriceAdjustment() != null) {
            variant.setPriceAdjustment(updateDto.getPriceAdjustment());
        }

        if (StringUtils.hasText(updateDto.getSku())) {
            if (!isSkuUnique(updateDto.getSku(), variantId)) {
                throw new IllegalArgumentException("Variant SKU already exists: " + updateDto.getSku());
            }
            variant.setSku(updateDto.getSku());
        }

        if (updateDto.getDisplayOrder() != null) {
            variant.setDisplayOrder(updateDto.getDisplayOrder());
        }

        if (updateDto.getIsActive() != null) {
            variant.setIsActive(updateDto.getIsActive());
        }

        if (updateDto.getImageUrl() != null) {
            variant.setImageUrl(updateDto.getImageUrl());
        }

        if (updateDto.getDescription() != null) {
            variant.setDescription(updateDto.getDescription());
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

    // Placeholder implementations for remaining methods
    @Override public List<ProductVariantDto> getVariantsByProductIdAndType(Long productId, VariantType variantType) { return List.of(); }
    @Override public List<ProductVariantDto> getActiveVariantsByProductIdAndType(Long productId, VariantType variantType) { return List.of(); }
    @Override public List<VariantType> getDistinctVariantTypesByProductId(Long productId) { return List.of(); }
    @Override public List<String> getDistinctValuesByProductIdAndType(Long productId, VariantType variantType) { return List.of(); }
    @Override public List<ProductVariantDto> searchVariantsByProductIdAndQuery(Long productId, String query) { return List.of(); }
    @Override public List<ProductVariantDto> getVariantsWithPriceAdjustment(Long productId) { return List.of(); }
    @Override public List<ProductVariantDto> getVariantsWithImages(Long productId) { return List.of(); }
    @Override public List<ProductVariantDto> getVariantsWithSku(Long productId) { return List.of(); }
    @Override public List<ProductVariantDto> createVariants(List<ProductVariantCreateDto> createDtos) { return List.of(); }
    @Override public List<ProductVariantDto> updateVariants(List<Long> variantIds, List<ProductVariantUpdateDto> updateDtos) { return List.of(); }
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
    @Override public void activateVariants(List<Long> variantIds) { }
    @Override public void deactivateVariants(List<Long> variantIds) { }
    @Override public long getVariantCountByProductId(Long productId) { return 0; }
    @Override public long getActiveVariantCountByProductId(Long productId) { return 0; }
    @Override public long getVariantCountByType(Long productId, VariantType variantType) { return 0; }
    @Override public void reorderVariants(Long productId, List<Long> variantIds) { }
    @Override public void moveVariantUp(Long variantId) { }
    @Override public void moveVariantDown(Long variantId) { }
}
