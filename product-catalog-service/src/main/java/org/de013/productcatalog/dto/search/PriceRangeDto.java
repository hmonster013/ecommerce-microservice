package org.de013.productcatalog.dto.search;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Price range filter")
public class PriceRangeDto {

    @DecimalMin(value = "0.00", message = "Minimum price must be non-negative")
    @Digits(integer = 8, fraction = 2, message = "Minimum price must have at most 8 integer digits and 2 decimal places")
    @Schema(description = "Minimum price", example = "100.00")
    private BigDecimal minPrice;

    @DecimalMin(value = "0.00", message = "Maximum price must be non-negative")
    @Digits(integer = 8, fraction = 2, message = "Maximum price must have at most 8 integer digits and 2 decimal places")
    @Schema(description = "Maximum price", example = "1000.00")
    private BigDecimal maxPrice;

    @Schema(description = "Price range label", example = "$100 - $1,000")
    private String label;

    // Validation method
    @JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message = "Maximum price must be greater than minimum price")
    public boolean isPriceRangeValid() {
        if (minPrice == null || maxPrice == null) {
            return true; // Allow partial ranges
        }
        return maxPrice.compareTo(minPrice) >= 0;
    }

    // Helper methods
    @JsonIgnore
    public boolean hasMinPrice() {
        return minPrice != null;
    }

    @JsonIgnore
    public boolean hasMaxPrice() {
        return maxPrice != null;
    }

    @JsonIgnore
    public boolean isValidRange() {
        return hasMinPrice() || hasMaxPrice();
    }

    @JsonIgnore
    public String getDisplayLabel() {
        if (label != null && !label.trim().isEmpty()) {
            return label;
        }
        
        if (hasMinPrice() && hasMaxPrice()) {
            return String.format("$%.2f - $%.2f", minPrice, maxPrice);
        } else if (hasMinPrice()) {
            return String.format("$%.2f and up", minPrice);
        } else if (hasMaxPrice()) {
            return String.format("Up to $%.2f", maxPrice);
        }
        
        return "Any price";
    }
}
