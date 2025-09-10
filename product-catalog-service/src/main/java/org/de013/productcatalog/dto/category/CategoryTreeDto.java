package org.de013.productcatalog.dto.category;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Category tree structure")
public class CategoryTreeDto {

    @Schema(description = "Category ID", example = "1")
    private Long id;

    @Schema(description = "Category name", example = "Electronics")
    private String name;

    @Schema(description = "Category slug", example = "electronics")
    private String slug;

    @Schema(description = "Category level in hierarchy", example = "0")
    private Integer level;

    @Schema(description = "Display order", example = "1")
    private Integer displayOrder;

    @Schema(description = "Is category active", example = "true")
    private Boolean isActive;

    @Schema(description = "Product count in this category", example = "25")
    private Integer productCount;

    @Schema(description = "Total product count (including subcategories)", example = "150")
    private Integer totalProductCount;

    @Schema(description = "Child categories")
    private List<CategoryTreeDto> children;

    @Schema(description = "Category path", example = "Electronics")
    private String path;

    @Schema(description = "Is expanded in tree view", example = "true")
    private Boolean expanded;

    @Schema(description = "Is selectable", example = "true")
    private Boolean selectable;

    @JsonIgnore
    public boolean isRootCategory() {
        return level != null && level == 0;
    }

    @JsonIgnore
    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    @JsonIgnore
    public boolean hasProducts() {
        return productCount != null && productCount > 0;
    }

    @JsonIgnore
    public int getChildrenCount() {
        return children != null ? children.size() : 0;
    }

    public void addChild(CategoryTreeDto child) {
        if (children == null) {
            children = new java.util.ArrayList<>();
        }
        children.add(child);
    }

    @JsonIgnore
    public String getDisplayText() {
        StringBuilder text = new StringBuilder(name);
        if (productCount != null && productCount > 0) {
            text.append(" (").append(productCount).append(")");
        }
        return text.toString();
    }
}
