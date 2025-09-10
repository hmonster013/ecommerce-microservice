package org.de013.productcatalog.mapper;

import org.de013.productcatalog.dto.category.CategoryResponseDto;
import org.de013.productcatalog.dto.category.CategorySummaryDto;
import org.de013.productcatalog.dto.category.CategoryTreeDto;
import org.de013.productcatalog.entity.Category;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class CategoryMapper {

    public CategorySummaryDto toCategorySummaryDto(Category category) {
        if (category == null) return null;

        return CategorySummaryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .level(category.getLevel())
                .displayOrder(category.getDisplayOrder())
                .isActive(category.getIsActive())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .parentName(category.getParent() != null ? category.getParent().getName() : null)
                .hasChildren(category.hasChildren())
                .build();
    }

    public CategoryResponseDto toCategoryResponseDto(Category category) {
        if (category == null) return null;

        return CategoryResponseDto.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .slug(category.getSlug())
                .parent(category.getParent() != null ? toCategorySummaryDto(category.getParent()) : null)
                .children(toCategorySummaryDtos(category.getChildren()))
                .level(category.getLevel())
                .displayOrder(category.getDisplayOrder())
                .isActive(category.getIsActive())
                .categoryPath(buildCategoryPath(category))
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .createdBy(category.getCreatedBy())
                .updatedBy(category.getUpdatedBy())
                .build();
    }

    public CategoryTreeDto toCategoryTreeDto(Category category) {
        if (category == null) return null;

        return CategoryTreeDto.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .level(category.getLevel())
                .displayOrder(category.getDisplayOrder())
                .isActive(category.getIsActive())
                .children(toCategoryTreeDtos(category.getChildren()))
                .path(buildCategoryPath(category))
                .expanded(false)
                .selectable(true)
                .build();
    }

    public List<CategorySummaryDto> toCategorySummaryDtos(List<Category> categories) {
        if (categories == null) return null;
        return categories.stream()
                .map(this::toCategorySummaryDto)
                .collect(Collectors.toList());
    }

    public List<CategoryTreeDto> toCategoryTreeDtos(List<Category> categories) {
        if (categories == null) return null;
        return categories.stream()
                .map(this::toCategoryTreeDto)
                .collect(Collectors.toList());
    }

    private String buildCategoryPath(Category category) {
        if (category.getParent() == null) {
            return category.getName();
        }
        return buildCategoryPath(category.getParent()) + " > " + category.getName();
    }

    /**
     * Convert Object[] from native query to CategorySummaryDto
     * Used for category path queries that return raw database results
     *
     * @param row Object array from native query: [id, name, slug, parent_id, level]
     * @return CategorySummaryDto
     */
    public CategorySummaryDto fromPathQueryResult(Object[] row) {
        if (row == null || row.length < 5) {
            return null;
        }

        return CategorySummaryDto.builder()
                .id(((Number) row[0]).longValue())
                .name((String) row[1])
                .slug((String) row[2])
                .level(((Number) row[4]).intValue())
                .build();
    }
}

