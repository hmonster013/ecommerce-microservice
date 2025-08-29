package org.de013.productcatalog.dto.product;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @NotBlank(message = "{product.name.required}")
    @Size(max = 255, message = "{product.name.too.long}")
    @Schema(description = "Product name", example = "iPhone 15 Pro", required = true)
    private String name;

    @Schema(description = "Product description", example = "Latest iPhone with advanced camera system")
    private String description;

    @Size(max = 500, message = "{product.short.description.too.long}")
    @Schema(description = "Short product description", example = "Premium smartphone with professional camera")
    private String shortDescription;

    @NotBlank(message = "{product.sku.required}")
    @ValidSku(message = "{ValidSku.message}")
    @Schema(description = "Product SKU", example = "PROD1234", required = true)
    private String sku;

    @NotNull(message = "{product.price.required}")
    @ValidPrice(min = 0.01, max = 999999.99, message = "{product.price.invalid}")
    @Schema(description = "Product price", example = "999.00", required = true)
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

    @Size(max = 255, message = "{field.too.long}")
    @Schema(description = "SEO meta title", example = "iPhone 15 Pro - Premium Smartphone")
    private String metaTitle;

    @Schema(description = "SEO meta description", example = "Latest iPhone with advanced camera system and A17 Pro chip")
    private String metaDescription;

    @Schema(description = "Search keywords", example = "iphone, apple, smartphone, mobile, camera, a17")
    private String searchKeywords;

    @NotEmpty(message = "{product.categories.required}")
    @Schema(description = "Category IDs", example = "[1, 6]", required = true)
    private List<Long> categoryIds;

    @Schema(description = "Primary category ID", example = "6")
    private Long primaryCategoryId;

    @Schema(description = "Initial inventory quantity", example = "50")
    @Min(value = 0, message = "{field.non.negative.required}")
    @Builder.Default
    private Integer initialQuantity = 0;

    @Schema(description = "Minimum stock level", example = "10")
    @Min(value = 0, message = "{field.non.negative.required}")
    @Builder.Default
    private Integer minStockLevel = 0;

    // Validation methods
    @JsonIgnore
    @AssertTrue(message = "Primary category must be included in category list")
    public boolean isPrimaryCategoryValid() {
        if (primaryCategoryId == null) {
            return true; // Optional field
        }
        return categoryIds != null && categoryIds.contains(primaryCategoryId);
    }

    @JsonIgnore
    @AssertTrue(message = "Compare price must be greater than or equal to price")
    public boolean isComparePriceValid() {
        if (comparePrice == null || price == null) {
            return true;
        }
        return comparePrice.compareTo(price) >= 0;
    }
}
