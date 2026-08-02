package org.de013.productcatalog.dto.category;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Category update request")
public record CategoryUpdateDto(

    @Size(max = 255, message = "{category.name.too.long}")
    @Schema(description = "Category name", example = "Smartphones")
    String name,

    @Schema(description = "Category description", example = "Mobile phones and accessories")
    String description,

    @Size(max = 255, message = "{field.too.long}")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "{ValidSlug.message}")
    @Schema(description = "Category slug", example = "smartphones")
    String slug,

    @Schema(description = "Parent category ID", example = "1")
    Long parentId,

    @Min(value = 0, message = "{category.display.order.invalid}")
    @Schema(description = "Display order", example = "1")
    Integer displayOrder,

    @Schema(description = "Is category active", example = "true")
    Boolean isActive
) {

    // Validation method
    @JsonIgnore
    @AssertTrue(message = "{ValidSlug.invalid.format}")
    public boolean isSlugValid() {
        if (slug == null || slug.trim().isEmpty()) {
            return true;
        }
        return slug.matches("^[a-z0-9-]+$") && !slug.startsWith("-") && !slug.endsWith("-");
    }

    // Helper method to check if any field is set
    @JsonIgnore
    public boolean hasUpdates() {
        return name != null || description != null || slug != null ||
                parentId != null || displayOrder != null || isActive != null;
    }
}
