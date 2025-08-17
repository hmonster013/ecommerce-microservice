package org.de013.productcatalog.dto.category;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Category creation request")
public class CategoryCreateDto {

    @NotBlank(message = "Category name is required")
    @Size(max = 255, message = "Category name must not exceed 255 characters")
    @Schema(description = "Category name", example = "Smartphones", required = true)
    private String name;

    @Schema(description = "Category description", example = "Mobile phones and accessories")
    private String description;

    @Size(max = 255, message = "Category slug must not exceed 255 characters")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase letters, numbers, and hyphens")
    @Schema(description = "Category slug (auto-generated if not provided)", example = "smartphones")
    private String slug;

    @Schema(description = "Parent category ID", example = "1")
    private Long parentId;

    @Min(value = 0, message = "Display order must be non-negative")
    @Schema(description = "Display order", example = "1")
    @Builder.Default
    private Integer displayOrder = 0;

    @Schema(description = "Is category active", example = "true")
    @Builder.Default
    private Boolean isActive = true;

    // Validation method
    @AssertTrue(message = "Slug must be unique and follow naming conventions")
    public boolean isSlugValid() {
        if (slug == null || slug.trim().isEmpty()) {
            return true; // Will be auto-generated
        }
        return slug.matches("^[a-z0-9-]+$") && !slug.startsWith("-") && !slug.endsWith("-");
    }
}
