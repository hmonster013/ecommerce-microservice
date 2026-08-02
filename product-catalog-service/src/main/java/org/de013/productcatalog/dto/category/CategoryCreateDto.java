package org.de013.productcatalog.dto.category;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.de013.productcatalog.validation.ValidSlug;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Category creation request")
public record CategoryCreateDto(

    @NotBlank(message = "{category.name.required}")
    @Size(max = 255, message = "{category.name.too.long}")
    @Schema(description = "Category name", example = "Smartphones", required = true)
    String name,

    @Schema(description = "Category description", example = "Mobile phones and accessories")
    String description,

    @ValidSlug(allowNull = true, message = "{ValidSlug.message}")
    @Schema(description = "Category slug (auto-generated if not provided)", example = "smartphones")
    String slug,

    @Schema(description = "Parent category ID", example = "1")
    Long parentId,

    @Min(value = 0, message = "{category.display.order.invalid}")
    @Schema(description = "Display order", example = "1")
    Integer displayOrder,

    @Schema(description = "Is category active", example = "true")
    Boolean isActive
) {
    public CategoryCreateDto {
        if (displayOrder == null) {
            displayOrder = 0;
        }
        if (isActive == null) {
            isActive = true;
        }
    }
}
