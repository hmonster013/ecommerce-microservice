package org.de013.productcatalog.dto.inventory;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Request to create variant inventory")
public record VariantInventoryCreateDto(

    @NotNull(message = "Variant ID is required")
    @Schema(description = "Variant ID", example = "1", required = true)
    Long variantId,

    @Min(value = 0, message = "Initial quantity must be non-negative")
    @Schema(description = "Initial quantity in stock", example = "100")
    Integer initialQuantity,

    @Min(value = 0, message = "Minimum stock level must be non-negative")
    @Schema(description = "Minimum stock level before low stock alert", example = "5")
    Integer minStockLevel,

    @Min(value = 0, message = "Maximum stock level must be non-negative")
    @Schema(description = "Maximum stock level for this variant", example = "200")
    Integer maxStockLevel,

    @Min(value = 0, message = "Reorder point must be non-negative")
    @Schema(description = "Stock level at which reorder should be triggered", example = "10")
    Integer reorderPoint,

    @Min(value = 0, message = "Reorder quantity must be non-negative")
    @Schema(description = "Quantity to reorder when reorder point is reached", example = "50")
    Integer reorderQuantity,

    @Schema(description = "Whether to track inventory for this variant", example = "true")
    Boolean trackInventory,

    @Schema(description = "Whether to allow backorders when out of stock", example = "false")
    Boolean allowBackorder,

    @Schema(description = "Physical location of the variant in warehouse", example = "A1-B2-C3")
    String location,

    @Schema(description = "Variant-specific SKU for inventory tracking", example = "PROD-001-L")
    String sku
) {
    public VariantInventoryCreateDto {
        if (initialQuantity == null) {
            initialQuantity = 0;
        }
        if (minStockLevel == null) {
            minStockLevel = 0;
        }
        if (reorderPoint == null) {
            reorderPoint = 0;
        }
        if (trackInventory == null) {
            trackInventory = true;
        }
        if (allowBackorder == null) {
            allowBackorder = false;
        }
    }

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
