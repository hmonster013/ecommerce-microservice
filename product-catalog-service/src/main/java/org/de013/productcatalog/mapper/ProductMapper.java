package org.de013.productcatalog.mapper;

import lombok.RequiredArgsConstructor;
import org.de013.productcatalog.dto.category.CategorySummaryDto;
import org.de013.productcatalog.dto.product.*;
import org.de013.productcatalog.entity.Product;
import org.de013.productcatalog.entity.ProductCategory;
import org.de013.productcatalog.entity.ProductImage;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProductMapper {

    private final CategoryMapper categoryMapper;
    private final ProductImageMapper productImageMapper;
    private final ProductVariantMapper productVariantMapper;
    private final InventoryMapper inventoryMapper;

    public ProductSummaryDto toProductSummaryDto(Product product) {
        if (product == null) return null;

        ProductSummaryDto.PricingInfo pricing = createPricingInfo(product.getPrice(), product.getComparePrice());

        return ProductSummaryDto.builder()
                .id(product.getId())
                .name(product.getName())
                .shortDescription(product.getShortDescription())
                .sku(product.getSku())
                .price(product.getPrice())
                .comparePrice(product.getComparePrice())
                .brand(product.getBrand())
                .status(product.getStatus())
                .isFeatured(product.getIsFeatured())
                .mainImageUrl(getMainImageUrl(product))
                .primaryCategoryName(getPrimaryCategoryName(product))
                .primaryCategorySlug(getPrimaryCategorySlug(product))
                .inStock(isProductInStock(product))
                .pricing(pricing)
                .build();
    }

    public ProductResponseDto toProductResponseDto(Product product) {
        if (product == null) return null;

        return ProductResponseDto.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .shortDescription(product.getShortDescription())
                .sku(product.getSku())
                .price(product.getPrice())
                .comparePrice(product.getComparePrice())
                .brand(product.getBrand())
                .weight(product.getWeight())
                .dimensions(product.getDimensions())
                .status(product.getStatus())
                .isFeatured(product.getIsFeatured())
                .isDigital(product.getIsDigital())
                .requiresShipping(product.getRequiresShipping())
                .metaTitle(product.getMetaTitle())
                .metaDescription(product.getMetaDescription())
                .searchKeywords(product.getSearchKeywords())
                .categories(toCategorySummaryDtos(product.getProductCategories()))
                .primaryCategory(getPrimaryCategorySummary(product))
                .images(productImageMapper.toProductImageDtos(product.getImages()))
                .mainImage(getMainImageDto(product))
                .variants(productVariantMapper.toProductVariantDtos(product.getVariants()))
                .inventory(inventoryMapper.toInventoryResponseDto(product.getInventory()))
                .pricing(createProductResponsePricingInfo(product.getPrice(), product.getComparePrice()))
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .createdBy(product.getCreatedBy())
                .updatedBy(product.getUpdatedBy())
                .build();
    }

    public ProductDetailDto toProductDetailDto(Product product) {
        if (product == null) return null;

        return ProductDetailDto.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .shortDescription(product.getShortDescription())
                .sku(product.getSku())
                .price(product.getPrice())
                .comparePrice(product.getComparePrice())
                .brand(product.getBrand())
                .weight(product.getWeight())
                .dimensions(product.getDimensions())
                .status(product.getStatus())
                .isFeatured(product.getIsFeatured())
                .isDigital(product.getIsDigital())
                .requiresShipping(product.getRequiresShipping())
                .metaTitle(product.getMetaTitle())
                .metaDescription(product.getMetaDescription())
                .searchKeywords(product.getSearchKeywords())
                .categories(toCategorySummaryDtos(product.getProductCategories()))
                .primaryCategory(getPrimaryCategorySummary(product))
                .images(productImageMapper.toProductImageDtos(product.getImages()))
                .variantGroups(productVariantMapper.toProductVariantGroupDtos(product.getVariants()))
                .inventory(inventoryMapper.toInventoryResponseDto(product.getInventory()))
                .pricing(createProductDetailPricingInfo(product.getPrice(), product.getComparePrice()))
                .shipping(createShippingInfo(product))
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    private ProductSummaryDto.PricingInfo createPricingInfo(BigDecimal price, BigDecimal comparePrice) {
        if (price == null) return null;

        boolean onSale = comparePrice != null && comparePrice.compareTo(price) > 0;
        BigDecimal discountAmount = onSale ? comparePrice.subtract(price) : BigDecimal.ZERO;
        BigDecimal discountPercentage = BigDecimal.ZERO;

        if (onSale && comparePrice.compareTo(BigDecimal.ZERO) > 0) {
            discountPercentage = discountAmount
                    .divide(comparePrice, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        String savingsText = onSale ?
            String.format("Save $%.2f (%.0f%%)", discountAmount, discountPercentage) : null;

        return ProductSummaryDto.PricingInfo.builder()
                .currentPrice(price)
                .originalPrice(comparePrice)
                .discountAmount(discountAmount)
                .discountPercentage(discountPercentage)
                .onSale(onSale)
                .savingsText(savingsText)
                .build();
    }

    private ProductResponseDto.PricingInfo createProductResponsePricingInfo(BigDecimal price, BigDecimal comparePrice) {
        if (price == null) return null;

        boolean onSale = comparePrice != null && comparePrice.compareTo(price) > 0;
        BigDecimal discountAmount = onSale ? comparePrice.subtract(price) : BigDecimal.ZERO;
        BigDecimal discountPercentage = BigDecimal.ZERO;

        if (onSale && comparePrice.compareTo(BigDecimal.ZERO) > 0) {
            discountPercentage = discountAmount
                    .divide(comparePrice, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        return ProductResponseDto.PricingInfo.builder()
                .currentPrice(price)
                .originalPrice(comparePrice)
                .discountAmount(discountAmount)
                .discountPercentage(discountPercentage)
                .onSale(onSale)
                .build();
    }

    private ProductDetailDto.PricingInfo createProductDetailPricingInfo(BigDecimal price, BigDecimal comparePrice) {
        if (price == null) return null;

        boolean onSale = comparePrice != null && comparePrice.compareTo(price) > 0;
        BigDecimal discountAmount = onSale ? comparePrice.subtract(price) : BigDecimal.ZERO;
        BigDecimal discountPercentage = BigDecimal.ZERO;

        if (onSale && comparePrice.compareTo(BigDecimal.ZERO) > 0) {
            discountPercentage = discountAmount
                    .divide(comparePrice, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        String savingsText = onSale ?
            String.format("Save $%.2f (%.0f%%)", discountAmount, discountPercentage) : null;

        return ProductDetailDto.PricingInfo.builder()
                .currentPrice(price)
                .originalPrice(comparePrice)
                .discountAmount(discountAmount)
                .discountPercentage(discountPercentage)
                .onSale(onSale)
                .savingsText(savingsText)
                .build();
    }

    private ProductDetailDto.ShippingInfo createShippingInfo(Product product) {
        return ProductDetailDto.ShippingInfo.builder()
                .requiresShipping(product.getRequiresShipping())
                .weight(product.getWeight())
                .dimensions(product.getDimensions())
                .freeShippingEligible(true) // Default logic
                .build();
    }

    private String getMainImageUrl(Product product) {
        if (product.getImages() == null || product.getImages().isEmpty()) {
            return null;
        }
        return product.getImages().stream()
                .filter(img -> "MAIN".equals(img.getImageType().name()) && img.getIsActive())
                .findFirst()
                .map(ProductImage::getUrl)
                .orElse(product.getImages().get(0).getUrl());
    }

    private String getPrimaryCategoryName(Product product) {
        return product.getProductCategories().stream()
                .filter(ProductCategory::getIsPrimary)
                .findFirst()
                .map(pc -> pc.getCategory().getName())
                .orElse(null);
    }

    private String getPrimaryCategorySlug(Product product) {
        return product.getProductCategories().stream()
                .filter(ProductCategory::getIsPrimary)
                .findFirst()
                .map(pc -> pc.getCategory().getSlug())
                .orElse(null);
    }

    private boolean isProductInStock(Product product) {
        return product.getInventory() != null && product.getInventory().isInStock();
    }

    private List<CategorySummaryDto> toCategorySummaryDtos(List<ProductCategory> productCategories) {
        if (productCategories == null) return null;
        return productCategories.stream()
                .map(pc -> categoryMapper.toCategorySummaryDto(pc.getCategory()))
                .collect(Collectors.toList());
    }

    private CategorySummaryDto getPrimaryCategorySummary(Product product) {
        return product.getProductCategories().stream()
                .filter(ProductCategory::getIsPrimary)
                .findFirst()
                .map(pc -> categoryMapper.toCategorySummaryDto(pc.getCategory()))
                .orElse(null);
    }

    private ProductImageDto getMainImageDto(Product product) {
        if (product.getImages() == null || product.getImages().isEmpty()) {
            return null;
        }

        return product.getImages().stream()
                .filter(img -> "MAIN".equals(img.getImageType().name()) && img.getIsActive())
                .findFirst()
                .map(productImageMapper::toProductImageDto)
                .orElse(productImageMapper.toProductImageDto(product.getImages().get(0)));
    }
}

