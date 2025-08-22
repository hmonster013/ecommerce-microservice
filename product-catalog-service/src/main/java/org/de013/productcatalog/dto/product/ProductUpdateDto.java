package org.de013.productcatalog.dto.product;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;
import org.de013.productcatalog.entity.enums.ProductStatus;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Product update request")
public class ProductUpdateDto {

    @Size(max = 255, message = "{product.name.too.long}")
    @Schema(description = "Product name", example = "iPhone 15 Pro")
    private String name;

    @Schema(description = "Product description", example = "Latest iPhone with advanced camera system")
    private String description;

    @Size(max = 500, message = "{product.short.description.too.long}")
    @Schema(description = "Short product description", example = "Premium smartphone with professional camera")
    private String shortDescription;

    @Size(max = 100, message = "{field.too.long}")
    @Pattern(regexp = "^[A-Z0-9-_]+$", message = "{field.invalid.format}")
    @Schema(description = "Product SKU", example = "IPHONE-15-PRO-128")
    private String sku;

    @DecimalMin(value = "0.01", message = "{field.positive.required}")
    @Digits(integer = 8, fraction = 2, message = "{field.invalid.format}")
    @Schema(description = "Product price", example = "999.00")
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "{field.positive.required}")
    @Digits(integer = 8, fraction = 2, message = "{field.invalid.format}")
    @Schema(description = "Compare at price (original price)", example = "1099.00")
    private BigDecimal comparePrice;

    @DecimalMin(value = "0.00", message = "{field.non.negative.required}")
    @Digits(integer = 8, fraction = 2, message = "{field.invalid.format}")
    @Schema(description = "Cost price", example = "750.00")
    private BigDecimal costPrice;

    @Size(max = 255, message = "{field.too.long}")
    @Schema(description = "Product brand", example = "Apple")
    private String brand;

    @DecimalMin(value = "0.00", message = "{product.weight.invalid}")
    @Digits(integer = 5, fraction = 3, message = "{field.invalid.format}")
    @Schema(description = "Product weight in kg", example = "0.187")
    private BigDecimal weight;

    @Size(max = 100, message = "{product.dimensions.invalid}")
    @Schema(description = "Product dimensions", example = "14.7 x 7.1 x 0.8 cm")
    private String dimensions;

    @Schema(description = "Product status", example = "ACTIVE")
    private ProductStatus status;

    @Schema(description = "Is featured product", example = "true")
    private Boolean isFeatured;

    @Schema(description = "Is digital product", example = "false")
    private Boolean isDigital;

    @Schema(description = "Requires shipping", example = "true")
    private Boolean requiresShipping;

    @Size(max = 255, message = "{field.too.long}")
    @Schema(description = "SEO meta title", example = "iPhone 15 Pro - Premium Smartphone")
    private String metaTitle;

    @Schema(description = "SEO meta description", example = "Latest iPhone with advanced camera system and A17 Pro chip")
    private String metaDescription;

    @Schema(description = "Search keywords", example = "iphone, apple, smartphone, mobile, camera, a17")
    private String searchKeywords;

    @Schema(description = "Category IDs", example = "[1, 6]")
    private List<Long> categoryIds;

    @Schema(description = "Primary category ID", example = "6")
    private Long primaryCategoryId;

    // Validation methods
    @AssertTrue(message = "Primary category must be included in category list")
    public boolean isPrimaryCategoryValid() {
        if (primaryCategoryId == null || categoryIds == null) {
            return true; // Optional fields
        }
        return categoryIds.contains(primaryCategoryId);
    }

    @AssertTrue(message = "Compare price must be greater than or equal to price")
    public boolean isComparePriceValid() {
        if (comparePrice == null || price == null) {
            return true;
        }
        return comparePrice.compareTo(price) >= 0;
    }

    // Helper method to check if any field is set
    public boolean hasUpdates() {
        return name != null || description != null || shortDescription != null ||
               sku != null || price != null || comparePrice != null || costPrice != null ||
               brand != null || weight != null || dimensions != null || status != null ||
               isFeatured != null || isDigital != null || requiresShipping != null ||
               metaTitle != null || metaDescription != null || searchKeywords != null ||
               categoryIds != null || primaryCategoryId != null;
    }
}
