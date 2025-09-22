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
@Schema(description = "Product variant creation request")
public class ProductVariantCreateDto {

    @JsonIgnore
    private Long productId;

    @NotNull(message = "{variant.type.required}")
    @Schema(description = "Variant type", example = "COLOR", required = true)
    private VariantType variantType;

    @NotBlank(message = "{variant.name.required}")
    @Size(max = 255, message = "{variant.name.too.long}")
    @Schema(description = "Variant name", example = "Color", required = true)
    private String name;

    @NotBlank(message = "{variant.value.required}")
    @Size(max = 255, message = "{variant.value.too.long}")
    @Schema(description = "Variant value", example = "Natural Titanium", required = true)
    private String value;

    @DecimalMin(value = "-999999.99", message = "{variant.price.adjustment.invalid}")
    @DecimalMax(value = "999999.99", message = "{variant.price.adjustment.invalid}")
    @Digits(integer = 8, fraction = 2, message = "{field.invalid.format}")
    @Schema(description = "Price adjustment (can be negative for discounts)", example = "0.00")
    @Builder.Default
    private BigDecimal priceAdjustment = BigDecimal.ZERO;

    @Size(max = 100, message = "{variant.sku.too.long}")
    @Pattern(regexp = "^[A-Z0-9-_]*$", message = "{variant.sku.invalid.format}")
    @Schema(description = "Variant SKU (optional)", example = "IPHONE-15-PRO-TITANIUM")
    private String sku;

    @Min(value = 0, message = "{field.non.negative.required}")
    @Schema(description = "Display order", example = "1")
    @Builder.Default
    private Integer displayOrder = 0;

    @Schema(description = "Is active", example = "true")
    @Builder.Default
    private Boolean isActive = true;

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
    @AssertTrue(message = "SKU must be unique if provided")
    public boolean isSkuValid() {
        // This will be validated at service level against database
        return true;
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
