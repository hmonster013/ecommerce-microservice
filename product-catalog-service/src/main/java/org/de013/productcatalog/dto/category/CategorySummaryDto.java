package org.de013.productcatalog.dto.category;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Category summary for listings")
public record CategorySummaryDto(

    @Schema(description = "Category ID", example = "1")
    Long id,

    @Schema(description = "Category name", example = "Smartphones")
    String name,

    @Schema(description = "Category slug", example = "smartphones")
    String slug,

    @Schema(description = "Category level in hierarchy", example = "1")
    Integer level,

    @Schema(description = "Display order", example = "1")
    Integer displayOrder,

    @Schema(description = "Is category active", example = "true")
    Boolean isActive,

    @Schema(description = "Product count in this category", example = "25")
    Integer productCount,

    @Schema(description = "Parent category ID", example = "1")
    Long parentId,

    @Schema(description = "Parent category name", example = "Electronics")
    String parentName,

    @Schema(description = "Has child categories", example = "false")
    Boolean hasChildren
) implements Serializable {

    private static final long serialVersionUID = 1L;

    // Helper methods
    @JsonIgnore
    public boolean isRootCategory() {
        return parentId == null;
    }

    @JsonIgnore
    public String getDisplayName() {
        return name;
    }

    @JsonIgnore
    public String getFullPath() {
        if (parentName != null) {
            return parentName + " > " + name;
        }
        return name;
    }
}
