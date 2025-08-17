package org.de013.productcatalog.dto.product;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;
import org.de013.productcatalog.entity.enums.ProductStatus;
import org.de013.productcatalog.validation.ValidPrice;
import org.de013.productcatalog.validation.ValidSku;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Product creation request")
public class ProductCreateDto {

    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name must not exceed 255 characters")
    @Schema(description = "Product name", example = "iPhone 15 Pro", required = true)
    private String name;

    @Schema(description = "Product description", example = "Latest iPhone with advanced camera system")
    private String description;

    @Size(max = 500, message = "Short description must not exceed 500 characters")
    @Schema(description = "Short product description", example = "Premium smartphone with professional camera")
    private String shortDescription;

    @NotBlank(message = "SKU is required")
    @ValidSku(message = "SKU must be 3-4 uppercase letters followed by 4-6 digits (e.g., ABC1234)")
    @Schema(description = "Product SKU", example = "PROD1234", required = true)
    private String sku;

    @NotNull(message = "Price is required")
    @ValidPrice(min = 0.01, max = 999999.99, message = "Price must be between $0.01 and $999,999.99")
    @Schema(description = "Product price", example = "999.00", required = true)
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "Compare price must be greater than 0")
    @Digits(integer = 8, fraction = 2, message = "Compare price must have at most 8 integer digits and 2 decimal places")
    @Schema(description = "Compare at price (original price)", example = "1099.00")
    private BigDecimal comparePrice;

    @DecimalMin(value = "0.00", message = "Cost price must be non-negative")
    @Digits(integer = 8, fraction = 2, message = "Cost price must have at most 8 integer digits and 2 decimal places")
    @Schema(description = "Cost price", example = "750.00")
    private BigDecimal costPrice;

    @Size(max = 255, message = "Brand must not exceed 255 characters")
    @Schema(description = "Product brand", example = "Apple")
    private String brand;

    @DecimalMin(value = "0.00", message = "Weight must be non-negative")
    @Digits(integer = 5, fraction = 3, message = "Weight must have at most 5 integer digits and 3 decimal places")
    @Schema(description = "Product weight in kg", example = "0.187")
    private BigDecimal weight;

    @Size(max = 100, message = "Dimensions must not exceed 100 characters")
    @Schema(description = "Product dimensions", example = "14.7 x 7.1 x 0.8 cm")
    private String dimensions;

    @Schema(description = "Product status", example = "ACTIVE")
    @Builder.Default
    private ProductStatus status = ProductStatus.ACTIVE;

    @Schema(description = "Is featured product", example = "true")
    @Builder.Default
    private Boolean isFeatured = false;

    @Schema(description = "Is digital product", example = "false")
    @Builder.Default
    private Boolean isDigital = false;

    @Schema(description = "Requires shipping", example = "true")
    @Builder.Default
    private Boolean requiresShipping = true;

    @Size(max = 255, message = "Meta title must not exceed 255 characters")
    @Schema(description = "SEO meta title", example = "iPhone 15 Pro - Premium Smartphone")
    private String metaTitle;

    @Schema(description = "SEO meta description", example = "Latest iPhone with advanced camera system and A17 Pro chip")
    private String metaDescription;

    @Schema(description = "Search keywords", example = "iphone, apple, smartphone, mobile, camera, a17")
    private String searchKeywords;

    @NotEmpty(message = "At least one category is required")
    @Schema(description = "Category IDs", example = "[1, 6]", required = true)
    private List<Long> categoryIds;

    @Schema(description = "Primary category ID", example = "6")
    private Long primaryCategoryId;

    @Schema(description = "Initial inventory quantity", example = "50")
    @Min(value = 0, message = "Initial quantity must be non-negative")
    @Builder.Default
    private Integer initialQuantity = 0;

    @Schema(description = "Minimum stock level", example = "10")
    @Min(value = 0, message = "Minimum stock level must be non-negative")
    @Builder.Default
    private Integer minStockLevel = 0;

    // Validation methods
    @AssertTrue(message = "Primary category must be included in category list")
    public boolean isPrimaryCategoryValid() {
        if (primaryCategoryId == null) {
            return true; // Optional field
        }
        return categoryIds != null && categoryIds.contains(primaryCategoryId);
    }

    @AssertTrue(message = "Compare price must be greater than or equal to price")
    public boolean isComparePriceValid() {
        if (comparePrice == null || price == null) {
            return true;
        }
        return comparePrice.compareTo(price) >= 0;
    }
}
