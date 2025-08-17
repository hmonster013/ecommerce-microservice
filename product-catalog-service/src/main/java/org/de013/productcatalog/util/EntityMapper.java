package org.de013.productcatalog.util;

import org.de013.productcatalog.dto.category.CategoryResponseDto;
import org.de013.productcatalog.dto.category.CategorySummaryDto;
import org.de013.productcatalog.dto.category.CategoryTreeDto;
import org.de013.productcatalog.dto.inventory.InventoryResponseDto;
import org.de013.productcatalog.dto.product.*;
import org.de013.productcatalog.dto.review.ReviewResponseDto;
import org.de013.productcatalog.dto.review.ReviewSummaryDto;
import org.de013.productcatalog.entity.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class EntityMapper {

    // Product mappings
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
                .images(toProductImageDtos(product.getImages()))
                .mainImage(getMainImageDto(product))
                .variants(toProductVariantDtos(product.getVariants()))
                .inventory(toInventoryResponseDto(product.getInventory()))
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
                .images(toProductImageDtos(product.getImages()))
                .variantGroups(toProductVariantGroupDtos(product.getVariants()))
                .inventory(toInventoryResponseDto(product.getInventory()))
                .pricing(createProductDetailPricingInfo(product.getPrice(), product.getComparePrice()))
                .shipping(createShippingInfo(product))
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    // Category mappings
    public CategorySummaryDto toCategorySummaryDto(Category category) {
        if (category == null) return null;

        return CategorySummaryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .level(category.getLevel())
                .displayOrder(category.getDisplayOrder())
                .isActive(category.getIsActive())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .parentName(category.getParent() != null ? category.getParent().getName() : null)
                .hasChildren(category.hasChildren())
                .build();
    }

    public CategoryResponseDto toCategoryResponseDto(Category category) {
        if (category == null) return null;

        return CategoryResponseDto.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .slug(category.getSlug())
                .parent(category.getParent() != null ? toCategorySummaryDto(category.getParent()) : null)
                .children(toCategorySummaryDtosFromCategories(category.getChildren()))
                .level(category.getLevel())
                .displayOrder(category.getDisplayOrder())
                .isActive(category.getIsActive())
                .categoryPath(buildCategoryPath(category))
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .createdBy(category.getCreatedBy())
                .updatedBy(category.getUpdatedBy())
                .build();
    }

    public CategoryTreeDto toCategoryTreeDto(Category category) {
        if (category == null) return null;

        return CategoryTreeDto.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .level(category.getLevel())
                .displayOrder(category.getDisplayOrder())
                .isActive(category.getIsActive())
                .children(toCategoryTreeDtos(category.getChildren()))
                .path(buildCategoryPath(category))
                .expanded(false)
                .selectable(true)
                .build();
    }

    // Helper methods
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

    private String buildCategoryPath(Category category) {
        if (category.getParent() == null) {
            return category.getName();
        }
        return buildCategoryPath(category.getParent()) + " > " + category.getName();
    }

    // Collection mapping methods
    private List<CategorySummaryDto> toCategorySummaryDtos(List<ProductCategory> productCategories) {
        if (productCategories == null) return null;
        return productCategories.stream()
                .map(pc -> toCategorySummaryDto(pc.getCategory()))
                .collect(Collectors.toList());
    }

    private List<CategorySummaryDto> toCategorySummaryDtosFromCategories(List<Category> categories) {
        if (categories == null) return null;
        return categories.stream()
                .map(this::toCategorySummaryDto)
                .collect(Collectors.toList());
    }

    private List<CategoryTreeDto> toCategoryTreeDtos(List<Category> categories) {
        if (categories == null) return null;
        return categories.stream()
                .map(this::toCategoryTreeDto)
                .collect(Collectors.toList());
    }

    private CategorySummaryDto getPrimaryCategorySummary(Product product) {
        return product.getProductCategories().stream()
                .filter(ProductCategory::getIsPrimary)
                .findFirst()
                .map(pc -> toCategorySummaryDto(pc.getCategory()))
                .orElse(null);
    }

    // DTO mapping methods implementation
    private List<ProductImageDto> toProductImageDtos(List<ProductImage> images) {
        if (images == null) return null;
        return images.stream()
                .map(this::toProductImageDto)
                .collect(Collectors.toList());
    }

    private ProductImageDto toProductImageDto(ProductImage image) {
        if (image == null) return null;

        return ProductImageDto.builder()
                .id(image.getId())
                .url(image.getUrl())
                .altText(image.getAltText())
                .imageType(image.getImageType().name())
                .displayOrder(image.getDisplayOrder())
                .isActive(image.getIsActive())
                .title(image.getTitle())
                .description(image.getDescription())
                .fileSize(image.getFileSize())
                .dimensions(image.getDimensions())
                .fileFormat(image.getFileFormat())
                .variantId(image.getVariant() != null ? image.getVariant().getId() : null)
                .build();
    }

    private ProductImageDto getMainImageDto(Product product) {
        if (product.getImages() == null || product.getImages().isEmpty()) {
            return null;
        }

        return product.getImages().stream()
                .filter(img -> "MAIN".equals(img.getImageType().name()) && img.getIsActive())
                .findFirst()
                .map(this::toProductImageDto)
                .orElse(toProductImageDto(product.getImages().get(0)));
    }

    private List<ProductVariantDto> toProductVariantDtos(List<ProductVariant> variants) {
        if (variants == null) return null;
        return variants.stream()
                .map(this::toProductVariantDto)
                .collect(Collectors.toList());
    }

    private ProductVariantDto toProductVariantDto(ProductVariant variant) {
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

    private List<ProductVariantGroupDto> toProductVariantGroupDtos(List<ProductVariant> variants) {
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

    public InventoryResponseDto toInventoryResponseDto(Inventory inventory) {
        if (inventory == null) return null;

        return InventoryResponseDto.builder()
                .id(inventory.getId())
                .productId(inventory.getProduct().getId())
                .quantity(inventory.getQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .availableQuantity(inventory.getAvailableQuantity())
                .minStockLevel(inventory.getMinStockLevel())
                .maxStockLevel(inventory.getMaxStockLevel())
                .reorderPoint(inventory.getReorderPoint())
                .reorderQuantity(inventory.getReorderQuantity())
                .trackInventory(inventory.getTrackInventory())
                .allowBackorder(inventory.getAllowBackorder())
                .location(inventory.getLocation())
                .supplierSku(inventory.getSupplierSku())
                .inStock(inventory.isInStock())
                .lowStock(inventory.isLowStock())
                .needsReorder(inventory.needsReorder())
                .stockStatus(inventory.getStockStatus())
                .build();
    }

    // Review mapping methods
    public ReviewResponseDto toReviewResponseDto(Review review) {
        if (review == null) return null;

        return ReviewResponseDto.builder()
                .id(review.getId())
                .productId(review.getProduct().getId())
                .productName(review.getProduct().getName())
                .userId(review.getUserId())
                .rating(review.getRating())
                .title(review.getTitle())
                .comment(review.getComment())
                .status(review.getStatus())
                .helpfulCount(review.getHelpfulCount())
                .notHelpfulCount(review.getNotHelpfulCount())
                .verifiedPurchase(review.getVerifiedPurchase())
                .reviewerName(review.getReviewerName())
                .moderationNotes(review.getModerationNotes())
                .moderatedBy(review.getModeratedBy())
                .moderatedAt(review.getModeratedAt())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .createdBy(review.getCreatedBy())
                .helpfulnessRatio(calculateHelpfulnessRatio(review))
                .ratingStars(generateRatingStars(review.getRating()))
                .build();
    }

    private Double calculateHelpfulnessRatio(Review review) {
        int total = review.getHelpfulCount() + review.getNotHelpfulCount();
        if (total == 0) return null;
        return (double) review.getHelpfulCount() / total;
    }

    private String generateRatingStars(Integer rating) {
        if (rating == null) return "";
        StringBuilder stars = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            stars.append(i <= rating ? "★" : "☆");
        }
        return stars.toString();
    }
}
