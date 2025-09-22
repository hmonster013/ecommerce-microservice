package org.de013.productcatalog.dto.inventory;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Request to create variant inventory")
public class VariantInventoryCreateDto {

    @NotNull(message = "Variant ID is required")
    @Schema(description = "Variant ID", example = "1", required = true)
    private Long variantId;

    @Min(value = 0, message = "Initial quantity must be non-negative")
    @Schema(description = "Initial quantity in stock", example = "100")
    @Builder.Default
    private Integer initialQuantity = 0;

    @Min(value = 0, message = "Minimum stock level must be non-negative")
    @Schema(description = "Minimum stock level before low stock alert", example = "5")
    @Builder.Default
    private Integer minStockLevel = 0;

    @Min(value = 0, message = "Maximum stock level must be non-negative")
    @Schema(description = "Maximum stock level for this variant", example = "200")
    private Integer maxStockLevel;

    @Min(value = 0, message = "Reorder point must be non-negative")
    @Schema(description = "Stock level at which reorder should be triggered", example = "10")
    @Builder.Default
    private Integer reorderPoint = 0;

    @Min(value = 0, message = "Reorder quantity must be non-negative")
    @Schema(description = "Quantity to reorder when reorder point is reached", example = "50")
    private Integer reorderQuantity;

    @Schema(description = "Whether to track inventory for this variant", example = "true")
    @Builder.Default
    private Boolean trackInventory = true;

    @Schema(description = "Whether to allow backorders when out of stock", example = "false")
    @Builder.Default
    private Boolean allowBackorder = false;

    @Schema(description = "Physical location of the variant in warehouse", example = "A1-B2-C3")
    private String location;

    @Schema(description = "Variant-specific SKU for inventory tracking", example = "PROD-001-L")
    private String sku;

    // Validation method
    public boolean isMaxStockLevelValid() {
        if (maxStockLevel == null || minStockLevel == null) {
            return true; // Optional fields
        }
        return maxStockLevel >= minStockLevel;
    }

    public boolean isReorderPointValid() {
        if (reorderPoint == null) {
            return true;
        }
        if (maxStockLevel != null) {
            return reorderPoint <= maxStockLevel;
        }
        return true;
    }
}
