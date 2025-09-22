package org.de013.productcatalog.dto.variant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;
import org.de013.productcatalog.entity.enums.VariantType;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Product variant update request")
public class ProductVariantUpdateDto {

    @Schema(description = "Variant type", example = "COLOR")
    private VariantType variantType;

    @Size(max = 255, message = "{variant.name.too.long}")
    @Schema(description = "Variant name", example = "Color")
    private String name;

    @Size(max = 255, message = "{variant.value.too.long}")
    @Schema(description = "Variant value", example = "Natural Titanium")
    private String value;

    @DecimalMin(value = "-999999.99", message = "{variant.price.adjustment.invalid}")
    @DecimalMax(value = "999999.99", message = "{variant.price.adjustment.invalid}")
    @Digits(integer = 8, fraction = 2, message = "{field.invalid.format}")
    @Schema(description = "Price adjustment (can be negative for discounts)", example = "0.00")
    private BigDecimal priceAdjustment;

    @Size(max = 100, message = "{variant.sku.too.long}")
    @Pattern(regexp = "^[A-Z0-9-_]*$", message = "{variant.sku.invalid.format}")
    @Schema(description = "Variant SKU", example = "IPHONE-15-PRO-TITANIUM")
    private String sku;

    @Min(value = 0, message = "{field.non.negative.required}")
    @Schema(description = "Display order", example = "1")
    private Integer displayOrder;

    @Schema(description = "Is active", example = "true")
    private Boolean isActive;

    @Size(max = 255, message = "{variant.image.url.too.long}")
    @Pattern(regexp = "^(https?://.*\\.(jpg|jpeg|png|gif|webp))?$", 
             message = "{variant.image.url.invalid.format}")
    @Schema(description = "Variant image URL", example = "https://example.com/images/iphone-15-pro-titanium.jpg")
    private String imageUrl;

    @Size(max = 2000, message = "{variant.description.too.long}")
    @Schema(description = "Variant description", example = "Natural Titanium finish with premium look")
    private String description;

    // Validation methods
    @JsonIgnore
    @AssertTrue(message = "At least one field must be provided for update")
    public boolean hasUpdates() {
        return variantType != null || name != null || value != null || 
               priceAdjustment != null || sku != null || displayOrder != null || 
               isActive != null || imageUrl != null || description != null;
    }

    @JsonIgnore
    @AssertTrue(message = "Image URL must be a valid HTTP/HTTPS URL ending with image extension")
    public boolean isImageUrlValid() {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return true; // Optional field
        }
        return imageUrl.matches("^https?://.*\\.(jpg|jpeg|png|gif|webp)$");
    }
}
