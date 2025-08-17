package org.de013.productcatalog.dto.product;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Product variant information")
public class ProductVariantDto {

    @Schema(description = "Variant ID", example = "1")
    private Long id;

    @Schema(description = "Variant type", example = "COLOR")
    private String variantType;

    @Schema(description = "Variant name", example = "Color")
    private String name;

    @Schema(description = "Variant value", example = "Natural Titanium")
    private String value;

    @Schema(description = "Price adjustment", example = "0.00")
    private BigDecimal priceAdjustment;

    @Schema(description = "Variant SKU", example = "IPHONE-15-PRO-TITANIUM")
    private String sku;

    @Schema(description = "Display order", example = "1")
    private Integer displayOrder;

    @Schema(description = "Is active", example = "true")
    private Boolean isActive;

    @Schema(description = "Variant image URL", example = "https://example.com/images/iphone-15-pro-titanium.jpg")
    private String imageUrl;

    @Schema(description = "Variant description", example = "Natural Titanium finish")
    private String description;

    @Schema(description = "Effective price (base price + adjustment)", example = "999.00")
    private BigDecimal effectivePrice;

    @Schema(description = "Full variant name", example = "Color: Natural Titanium")
    private String fullName;

    @Schema(description = "Has additional cost", example = "false")
    private Boolean hasAdditionalCost;

    @Schema(description = "Has discount", example = "false")
    private Boolean hasDiscount;
}
