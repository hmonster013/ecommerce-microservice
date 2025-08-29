package org.de013.productcatalog.dto.category;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Category summary for listings")
public class CategorySummaryDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Category ID", example = "1")
    private Long id;

    @Schema(description = "Category name", example = "Smartphones")
    private String name;

    @Schema(description = "Category slug", example = "smartphones")
    private String slug;

    @Schema(description = "Category level in hierarchy", example = "1")
    private Integer level;

    @Schema(description = "Display order", example = "1")
    private Integer displayOrder;

    @Schema(description = "Is category active", example = "true")
    private Boolean isActive;

    @Schema(description = "Product count in this category", example = "25")
    private Integer productCount;

    @Schema(description = "Parent category ID", example = "1")
    private Long parentId;

    @Schema(description = "Parent category name", example = "Electronics")
    private String parentName;

    @Schema(description = "Has child categories", example = "false")
    private Boolean hasChildren;

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
