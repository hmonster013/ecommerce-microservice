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

    // Category Search
    List<CategorySummaryDto> searchCategories(String query);

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

    // Category Statistics
    long getTotalCategoryCount();

    long getActiveCategoryCount();

    long getCategoryCountByLevel(Integer level);

    long getChildCategoryCount(Long parentId);

    // Category Display Order
    void moveCategory(Long categoryId, Long newParentId);

    // Category Relationships
    boolean hasChildren(Long categoryId);

    boolean hasProducts(Long categoryId);

    boolean canBeDeleted(Long categoryId);

    // Cache Operations
    void clearCategoryCache();

    void clearCategoryCache(Long categoryId);

    // Category Existence Checks
    boolean existsById(Long id);
    
    boolean existsBySlug(String slug);
    
    boolean existsByParentId(Long parentId);
}
