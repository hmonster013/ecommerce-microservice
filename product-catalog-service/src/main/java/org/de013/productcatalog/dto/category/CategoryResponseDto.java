package org.de013.productcatalog.dto.category;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Category response")
public class CategoryResponseDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Category ID", example = "1")
    private Long id;

    @Schema(description = "Category name", example = "Smartphones")
    private String name;

    @Schema(description = "Category description", example = "Mobile phones and accessories")
    private String description;

    @Schema(description = "Category slug", example = "smartphones")
    private String slug;

    @Schema(description = "Parent category")
    private CategorySummaryDto parent;

    @Schema(description = "Child categories")
    private List<CategorySummaryDto> children;

    @Schema(description = "Category level in hierarchy", example = "1")
    private Integer level;

    @Schema(description = "Display order", example = "1")
    private Integer displayOrder;

    @Schema(description = "Is category active", example = "true")
    private Boolean isActive;

    @Schema(description = "Product count in this category", example = "25")
    private Integer productCount;

    @Schema(description = "Total product count (including subcategories)", example = "150")
    private Integer totalProductCount;

    @Schema(description = "Category path", example = "Electronics > Smartphones")
    private String categoryPath;

    @Schema(description = "Category breadcrumbs")
    private List<CategoryBreadcrumb> breadcrumbs;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "Creation timestamp", example = "2024-01-15 10:30:00")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "Last update timestamp", example = "2024-01-15 10:30:00")
    private LocalDateTime updatedAt;

    @Schema(description = "Created by", example = "admin")
    private String createdBy;

    @Schema(description = "Updated by", example = "admin")
    private String updatedBy;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Category breadcrumb")
    public static class CategoryBreadcrumb implements Serializable {

        private static final long serialVersionUID = 1L;
        
        @Schema(description = "Category ID", example = "1")
        private Long id;
        
        @Schema(description = "Category name", example = "Electronics")
        private String name;
        
        @Schema(description = "Category slug", example = "electronics")
        private String slug;
        
        @Schema(description = "Level in hierarchy", example = "0")
        private Integer level;
    }

    // Helper methods
    @JsonIgnore
    public boolean isRootCategory() {
        return parent == null;
    }

    @JsonIgnore
    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    @JsonIgnore
    public boolean hasProducts() {
        return productCount != null && productCount > 0;
    }
}
