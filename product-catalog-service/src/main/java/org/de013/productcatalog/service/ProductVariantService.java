package org.de013.productcatalog.service;

import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.dto.product.ProductVariantDto;
import org.de013.productcatalog.dto.product.ProductVariantGroupDto;
import org.de013.productcatalog.dto.variant.ProductVariantCreateDto;
import org.de013.productcatalog.dto.variant.ProductVariantUpdateDto;
import org.de013.productcatalog.entity.enums.VariantType;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductVariantService {

    // CRUD Operations
    ProductVariantDto createVariant(Long productId, ProductVariantCreateDto createDto);
    
    ProductVariantDto updateVariant(Long variantId, ProductVariantUpdateDto updateDto);
    
    void deleteVariant(Long variantId);
    
    ProductVariantDto getVariantById(Long variantId);
    
    ProductVariantDto getVariantBySku(String sku);

    // Product-specific Operations
    List<ProductVariantDto> getVariantsByProductId(Long productId);
    
    List<ProductVariantDto> getActiveVariantsByProductId(Long productId);
    
    List<ProductVariantGroupDto> getVariantGroupsByProductId(Long productId);
    
    PageResponse<ProductVariantDto> getVariantsByProductId(Long productId, Pageable pageable);

    // Variant Type Operations
    List<ProductVariantDto> getVariantsByProductIdAndType(Long productId, VariantType variantType);
    
    List<ProductVariantDto> getActiveVariantsByProductIdAndType(Long productId, VariantType variantType);
    
    List<VariantType> getDistinctVariantTypesByProductId(Long productId);
    
    List<String> getDistinctValuesByProductIdAndType(Long productId, VariantType variantType);

    // Search and Filter Operations
    List<ProductVariantDto> searchVariantsByProductIdAndQuery(Long productId, String query);
    
    List<ProductVariantDto> getVariantsWithPriceAdjustment(Long productId);
    
    List<ProductVariantDto> getVariantsWithImages(Long productId);
    
    List<ProductVariantDto> getVariantsWithSku(Long productId);

    // Bulk Operations
    List<ProductVariantDto> createVariants(List<ProductVariantCreateDto> createDtos);
    
    List<ProductVariantDto> updateVariants(List<Long> variantIds, List<ProductVariantUpdateDto> updateDtos);
    
    void deleteVariants(List<Long> variantIds);
    
    void activateVariants(List<Long> variantIds);
    
    void deactivateVariants(List<Long> variantIds);

    // Validation Operations
    boolean isSkuUnique(String sku);
    
    boolean isSkuUnique(String sku, Long excludeVariantId);
    
    boolean isVariantCombinationUnique(Long productId, VariantType variantType, String value);
    
    boolean isVariantCombinationUnique(Long productId, VariantType variantType, String value, Long excludeVariantId);
    
    void validateVariantData(ProductVariantCreateDto createDto);
    
    void validateVariantData(ProductVariantUpdateDto updateDto, Long variantId);

    // Statistics Operations
    long getVariantCountByProductId(Long productId);
    
    long getActiveVariantCountByProductId(Long productId);
    
    long getVariantCountByType(Long productId, VariantType variantType);

    // Display Order Operations
    void reorderVariants(Long productId, List<Long> variantIds);
    
    void moveVariantUp(Long variantId);
    
    void moveVariantDown(Long variantId);
}
