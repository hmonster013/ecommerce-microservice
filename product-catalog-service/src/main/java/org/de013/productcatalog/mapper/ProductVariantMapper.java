package org.de013.productcatalog.mapper;

import org.de013.productcatalog.dto.product.ProductVariantDto;
import org.de013.productcatalog.dto.product.ProductVariantGroupDto;
import org.de013.productcatalog.entity.ProductVariant;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ProductVariantMapper {

    public ProductVariantDto toProductVariantDto(ProductVariant variant) {
        if (variant == null) return null;

        return ProductVariantDto.builder()
                .id(variant.getId())
                .variantType(variant.getVariantType().name())
                .name(variant.getName())
                .value(variant.getValue())
                .priceAdjustment(variant.getPriceAdjustment())
                .sku(variant.getSku())
                .displayOrder(variant.getDisplayOrder())
                .isActive(variant.getIsActive())
                .imageUrl(variant.getImageUrl())
                .description(variant.getDescription())
                .effectivePrice(variant.getEffectivePrice())
                .fullName(variant.getFullName())
                .hasAdditionalCost(variant.hasAdditionalCost())
                .hasDiscount(variant.hasDiscount())
                .build();
    }

    public List<ProductVariantDto> toProductVariantDtos(List<ProductVariant> variants) {
        if (variants == null) return null;
        return variants.stream()
                .map(this::toProductVariantDto)
                .collect(Collectors.toList());
    }

    public List<ProductVariantGroupDto> toProductVariantGroupDtos(List<ProductVariant> variants) {
        if (variants == null) return null;

        // Group variants by type
        return variants.stream()
                .collect(Collectors.groupingBy(ProductVariant::getVariantType))
                .entrySet().stream()
                .map(entry -> ProductVariantGroupDto.builder()
                        .variantType(entry.getKey().name())
                        .displayName(entry.getKey().getDisplayName())
                        .description(entry.getKey().getDescription())
                        .variants(toProductVariantDtos(entry.getValue()))
                        .required(true)
                        .multipleSelection(false)
                        .displayOrder(entry.getKey().ordinal())
                        .build())
                .collect(Collectors.toList());
    }
}

