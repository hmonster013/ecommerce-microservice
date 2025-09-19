package org.de013.productcatalog.service;

import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.dto.category.*;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CategoryService {

    // CRUD Operations
    CategoryResponseDto createCategory(CategoryCreateDto createDto);
    
    CategoryResponseDto updateCategory(Long id, CategoryUpdateDto updateDto);
    
    void deleteCategory(Long id);
    
    CategoryResponseDto getCategoryById(Long id);
    
    CategoryResponseDto getCategoryBySlug(String slug);

    // Listing Operations
    PageResponse<CategoryResponseDto> getAllCategories(Pageable pageable);
    
    List<CategorySummaryDto> getAllActiveCategories();
    
    List<CategorySummaryDto> getAllActiveCategoriesOrdered();

    // Hierarchy Operations
    List<CategoryTreeDto> getCategoryTree(Integer maxLevel);
    
    List<CategorySummaryDto> getRootCategories();
    
    List<CategorySummaryDto> getChildCategories(Long parentId);
    
    List<CategorySummaryDto> getCategoryPath(Long categoryId);
    
    List<CategorySummaryDto> getCategoryAncestors(Long categoryId);
    
    List<CategorySummaryDto> getCategoryDescendants(Long categoryId);

    // Category Tree Building
    CategoryTreeDto buildCategoryTree(Long rootCategoryId);
    
    List<CategoryTreeDto> buildCategoryTrees(List<Long> rootCategoryIds);
    
    CategoryTreeDto buildFullCategoryTree();

    // Category with Product Count
    List<CategorySummaryDto> getCategoriesWithProductCount();
    
    List<CategorySummaryDto> getRootCategoriesWithProductCount();
    
    List<CategorySummaryDto> getChildCategoriesWithProductCount(Long parentId);

    // Popular Categories
    PageResponse<CategorySummaryDto> getPopularCategories(Pageable pageable);
    
    List<CategorySummaryDto> getPopularCategories(int limit);

    // Category Search
    List<CategorySummaryDto> searchCategories(String query);
    
    PageResponse<CategorySummaryDto> searchCategories(String query, Pageable pageable);

    // Category Validation
    boolean isSlugUnique(String slug);
    
    boolean isSlugUnique(String slug, Long excludeCategoryId);
    
    void validateCategoryData(CategoryCreateDto createDto);
    
    void validateCategoryData(CategoryUpdateDto updateDto, Long categoryId);
    
    void validateCategoryHierarchy(Long parentId, Long categoryId);

    // Slug Operations
    String generateSlug(String name);
    
    String generateUniqueSlug(String name);
    
    String generateUniqueSlug(String name, Long excludeCategoryId);

    // Category Status Operations
    CategoryResponseDto updateCategoryStatus(Long id, boolean isActive);
    
    List<CategoryResponseDto> bulkUpdateActiveStatus(List<Long> categoryIds, boolean active);

    // Category Statistics
    long getTotalCategoryCount();
    
    long getActiveCategoryCount();
    
    long getCategoryCountByLevel(Integer level);
    
    long getChildCategoryCount(Long parentId);

    // Category Level Operations
    List<CategorySummaryDto> getCategoriesByLevel(Integer level);
    
    Integer getMaxCategoryLevel();
    
    void updateCategoryLevels(Long parentId);

    // Category Display Order
    void updateDisplayOrder(Long categoryId, Integer newOrder);
    
    void reorderCategories(List<Long> categoryIds);
    
    void moveCategory(Long categoryId, Long newParentId);
    
    void moveCategoryUp(Long categoryId);
    
    void moveCategoryDown(Long categoryId);

    // Category Relationships
    boolean hasChildren(Long categoryId);
    
    boolean hasProducts(Long categoryId);
    
    boolean canBeDeleted(Long categoryId);
    
    List<CategorySummaryDto> getRelatedCategories(Long categoryId);

    // Breadcrumb Operations
    List<CategoryResponseDto.CategoryBreadcrumb> getCategoryBreadcrumbs(Long categoryId);

    String getCategoryPathString(Long categoryId);

    // Category Maintenance
    void cleanupEmptyCategories();
    
    void rebuildCategoryTree();
    
    void validateCategoryIntegrity();

    // Bulk Operations
    List<CategorySummaryDto> getCategoriesByIds(List<Long> categoryIds);
    
    void bulkDeleteCategories(List<Long> categoryIds);

    // Cache Operations
    void clearCategoryCache();
    
    void clearCategoryCache(Long categoryId);
    
    void refreshCategoryCache(Long categoryId);

    // Category Export/Import
    List<CategoryTreeDto> exportCategoryTree();
    
    void importCategoryTree(List<CategoryTreeDto> categoryTree);

    // Category Existence Checks
    boolean existsById(Long id);
    
    boolean existsBySlug(String slug);
    
    boolean existsByParentId(Long parentId);
}
