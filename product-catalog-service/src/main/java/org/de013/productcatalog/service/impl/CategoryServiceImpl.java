package org.de013.productcatalog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.dto.category.*;
import org.de013.productcatalog.entity.Category;
import org.de013.productcatalog.exception.CategoryNotFoundException;
import org.de013.productcatalog.exception.InvalidCategoryHierarchyException;
import org.de013.productcatalog.repository.CategoryRepository;
import org.de013.productcatalog.repository.ProductCategoryRepository;
import org.de013.productcatalog.service.CategoryService;
import org.de013.productcatalog.util.EntityMapper;
import org.de013.productcatalog.util.SlugUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final EntityMapper entityMapper;

    @Override
    @Transactional
    public CategoryResponseDto createCategory(CategoryCreateDto createDto) {
        log.info("Creating category with name: {}", createDto.getName());
        
        validateCategoryData(createDto);
        
        // Generate slug if not provided
        String slug = StringUtils.hasText(createDto.getSlug()) ? 
                createDto.getSlug() : generateUniqueSlug(createDto.getName());
        
        // Determine level and parent
        Category parent = null;
        int level = 0;
        if (createDto.getParentId() != null) {
            parent = findCategoryById(createDto.getParentId());
            level = parent.getLevel() + 1;
        }
        
        // Determine display order
        Integer displayOrder = createDto.getDisplayOrder();
        if (displayOrder == null) {
            displayOrder = categoryRepository.findMaxDisplayOrderBySiblings(createDto.getParentId()) + 1;
        }
        
        Category category = Category.builder()
                .name(createDto.getName())
                .description(createDto.getDescription())
                .slug(slug)
                .parent(parent)
                .level(level)
                .displayOrder(displayOrder)
                .isActive(createDto.getIsActive())
                .build();
        
        category = categoryRepository.save(category);
        
        log.info("Category created successfully with ID: {}", category.getId());
        return entityMapper.toCategoryResponseDto(category);
    }

    @Override
    @Transactional
    @CacheEvict(value = "categories", key = "#id")
    public CategoryResponseDto updateCategory(Long id, CategoryUpdateDto updateDto) {
        log.info("Updating category with ID: {}", id);
        
        Category category = findCategoryById(id);
        validateCategoryData(updateDto, id);
        
        // Update fields
        updateCategoryFields(category, updateDto);
        
        // Handle parent change
        if (updateDto.getParentId() != null && !updateDto.getParentId().equals(
                category.getParent() != null ? category.getParent().getId() : null)) {
            updateCategoryParent(category, updateDto.getParentId());
        }
        
        category = categoryRepository.save(category);
        
        log.info("Category updated successfully with ID: {}", id);
        return entityMapper.toCategoryResponseDto(category);
    }

    @Override
    @Transactional
    @CacheEvict(value = "categories", key = "#id")
    public void deleteCategory(Long id) {
        log.info("Deleting category with ID: {}", id);
        
        Category category = findCategoryById(id);
        
        if (!canBeDeleted(id)) {
            long childCount = categoryRepository.countByParentIdAndIsActiveTrue(id);
            long productCount = productCategoryRepository.countByCategoryId(id);

            if (childCount > 0) {
                throw InvalidCategoryHierarchyException.cannotDeleteWithChildren(id, (int) childCount);
            }
            if (productCount > 0) {
                throw InvalidCategoryHierarchyException.cannotDeleteWithProducts(id, (int) productCount);
            }
        }
        
        categoryRepository.delete(category);
        
        log.info("Category deleted successfully with ID: {}", id);
    }

    @Override
    @Cacheable(value = "categories", key = "#id")
    public CategoryResponseDto getCategoryById(Long id) {
        log.debug("Getting category by ID: {}", id);
        
        Category category = findCategoryById(id);
        return entityMapper.toCategoryResponseDto(category);
    }

    @Override
    @Cacheable(value = "categories", key = "#slug")
    public CategoryResponseDto getCategoryBySlug(String slug) {
        log.debug("Getting category by slug: {}", slug);
        
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new CategoryNotFoundException(slug));
        
        return entityMapper.toCategoryResponseDto(category);
    }

    @Override
    @Cacheable(value = "categories", key = "'all:' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public PageResponse<CategoryResponseDto> getAllCategories(Pageable pageable) {
        log.debug("Getting all categories with pagination: {}", pageable);
        
        Page<Category> categories = categoryRepository.findAll(pageable);
        List<CategoryResponseDto> content = categories.getContent().stream()
                .map(entityMapper::toCategoryResponseDto)
                .collect(Collectors.toList());
        
        return PageResponse.<CategoryResponseDto>builder()
                .content(content)
                .page(categories.getNumber())
                .size(categories.getSize())
                .totalElements(categories.getTotalElements())
                .totalPages(categories.getTotalPages())
                .first(categories.isFirst())
                .last(categories.isLast())
                .empty(categories.isEmpty())
                .build();
    }

    @Override
    @Cacheable(value = "categories", key = "'all_active'")
    public List<CategorySummaryDto> getAllActiveCategories() {
        log.debug("Getting all active categories");
        
        List<Category> categories = categoryRepository.findByIsActiveTrue();
        return categories.stream()
                .map(entityMapper::toCategorySummaryDto)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "categories", key = "'all_active_ordered'")
    public List<CategorySummaryDto> getAllActiveCategoriesOrdered() {
        log.debug("Getting all active categories ordered");
        
        List<Category> categories = categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        return categories.stream()
                .map(entityMapper::toCategorySummaryDto)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "categories", key = "'tree'")
    public List<CategoryTreeDto> getCategoryTree() {
        log.debug("Getting category tree");
        
        List<Category> rootCategories = categoryRepository.findRootCategories();
        return rootCategories.stream()
                .map(this::buildCategoryTreeRecursive)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "categories", key = "'tree_level_' + #maxLevel")
    public List<CategoryTreeDto> getCategoryTree(Integer maxLevel) {
        log.debug("Getting category tree with max level: {}", maxLevel);
        
        List<Category> categories = categoryRepository.findCategoriesUpToLevel(maxLevel);
        return buildCategoryTreeFromFlat(categories);
    }

    @Override
    @Cacheable(value = "categories", key = "'root'")
    public List<CategorySummaryDto> getRootCategories() {
        log.debug("Getting root categories");
        
        List<Category> categories = categoryRepository.findByParentIsNullAndIsActiveTrueOrderByDisplayOrderAsc();
        return categories.stream()
                .map(entityMapper::toCategorySummaryDto)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "categories", key = "'children_' + #parentId")
    public List<CategorySummaryDto> getChildCategories(Long parentId) {
        log.debug("Getting child categories for parent ID: {}", parentId);
        
        List<Category> categories = categoryRepository.findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(parentId);
        return categories.stream()
                .map(entityMapper::toCategorySummaryDto)
                .collect(Collectors.toList());
    }

    @Override
    public String generateSlug(String name) {
        return SlugUtils.generateSlug(name);
    }

    @Override
    public String generateUniqueSlug(String name) {
        return generateUniqueSlug(name, null);
    }

    @Override
    public String generateUniqueSlug(String name, Long excludeCategoryId) {
        return SlugUtils.generateUniqueSlug(name, slug -> {
            if (excludeCategoryId != null) {
                return categoryRepository.existsBySlugAndIdNot(slug, excludeCategoryId);
            } else {
                return categoryRepository.existsBySlug(slug);
            }
        });
    }

    @Override
    public boolean isSlugUnique(String slug) {
        return !categoryRepository.existsBySlug(slug);
    }

    @Override
    public boolean isSlugUnique(String slug, Long excludeCategoryId) {
        if (excludeCategoryId == null) {
            return isSlugUnique(slug);
        }
        return !categoryRepository.existsBySlugAndIdNot(slug, excludeCategoryId);
    }

    @Override
    public void validateCategoryData(CategoryCreateDto createDto) {
        // Validate slug uniqueness
        String slug = StringUtils.hasText(createDto.getSlug()) ? 
                createDto.getSlug() : generateSlug(createDto.getName());
        
        if (!isSlugUnique(slug)) {
            throw new RuntimeException("Category slug already exists: " + slug);
        }
        
        // Validate parent exists
        if (createDto.getParentId() != null && !categoryRepository.existsById(createDto.getParentId())) {
            throw new RuntimeException("Parent category not found with ID: " + createDto.getParentId());
        }
    }

    @Override
    public void validateCategoryData(CategoryUpdateDto updateDto, Long categoryId) {
        // Validate slug uniqueness if provided
        if (StringUtils.hasText(updateDto.getSlug()) && !isSlugUnique(updateDto.getSlug(), categoryId)) {
            throw new RuntimeException("Category slug already exists: " + updateDto.getSlug());
        }
        
        // Validate parent exists and not creating circular reference
        if (updateDto.getParentId() != null) {
            validateCategoryHierarchy(updateDto.getParentId(), categoryId);
        }
    }

    @Override
    public void validateCategoryHierarchy(Long parentId, Long categoryId) {
        if (parentId.equals(categoryId)) {
            throw new RuntimeException("Category cannot be its own parent");
        }
        
        if (!categoryRepository.existsById(parentId)) {
            throw new RuntimeException("Parent category not found with ID: " + parentId);
        }
        
        // Check for circular reference by checking if parentId is a descendant of categoryId
        // This would require a recursive check - simplified for now
    }

    // Helper methods
    private Category findCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
    }

    private CategoryTreeDto buildCategoryTreeRecursive(Category category) {
        CategoryTreeDto treeDto = entityMapper.toCategoryTreeDto(category);
        
        List<Category> children = categoryRepository.findChildCategories(category.getId());
        if (!children.isEmpty()) {
            List<CategoryTreeDto> childrenDtos = children.stream()
                    .map(this::buildCategoryTreeRecursive)
                    .collect(Collectors.toList());
            treeDto.setChildren(childrenDtos);
        }
        
        return treeDto;
    }

    private List<CategoryTreeDto> buildCategoryTreeFromFlat(List<Category> categories) {
        // TODO: Implement tree building from flat list
        return categories.stream()
                .filter(c -> c.getParent() == null)
                .map(entityMapper::toCategoryTreeDto)
                .collect(Collectors.toList());
    }

    private void updateCategoryFields(Category category, CategoryUpdateDto updateDto) {
        if (StringUtils.hasText(updateDto.getName())) {
            category.setName(updateDto.getName());
        }
        if (updateDto.getDescription() != null) {
            category.setDescription(updateDto.getDescription());
        }
        if (StringUtils.hasText(updateDto.getSlug())) {
            category.setSlug(updateDto.getSlug());
        }
        if (updateDto.getDisplayOrder() != null) {
            category.setDisplayOrder(updateDto.getDisplayOrder());
        }
        if (updateDto.getIsActive() != null) {
            category.setIsActive(updateDto.getIsActive());
        }
    }

    private void updateCategoryParent(Category category, Long newParentId) {
        Category newParent = newParentId != null ? findCategoryById(newParentId) : null;
        category.setParent(newParent);
        category.setLevel(newParent != null ? newParent.getLevel() + 1 : 0);
    }

    // Placeholder implementations for remaining methods
    @Override public List<CategorySummaryDto> getCategoryPath(Long categoryId) { return List.of(); }
    @Override public List<CategorySummaryDto> getCategoryAncestors(Long categoryId) { return List.of(); }
    @Override public List<CategorySummaryDto> getCategoryDescendants(Long categoryId) { return List.of(); }
    @Override public CategoryTreeDto buildCategoryTree(Long rootCategoryId) { return null; }
    @Override public List<CategoryTreeDto> buildCategoryTrees(List<Long> rootCategoryIds) { return List.of(); }
    @Override public CategoryTreeDto buildFullCategoryTree() { return null; }
    @Override public List<CategorySummaryDto> getCategoriesWithProductCount() { return List.of(); }
    @Override public List<CategorySummaryDto> getRootCategoriesWithProductCount() { return List.of(); }
    @Override public List<CategorySummaryDto> getChildCategoriesWithProductCount(Long parentId) { return List.of(); }
    @Override public PageResponse<CategorySummaryDto> getPopularCategories(Pageable pageable) { return null; }
    @Override public List<CategorySummaryDto> getPopularCategories(int limit) { return List.of(); }
    @Override public List<CategorySummaryDto> searchCategories(String query) { return List.of(); }
    @Override public PageResponse<CategorySummaryDto> searchCategories(String query, Pageable pageable) { return null; }
    @Override public CategoryResponseDto activateCategory(Long id) { return null; }
    @Override public CategoryResponseDto deactivateCategory(Long id) { return null; }
    @Override public List<CategoryResponseDto> bulkUpdateActiveStatus(List<Long> categoryIds, boolean active) { return List.of(); }
    @Override public long getTotalCategoryCount() { return categoryRepository.count(); }
    @Override public long getActiveCategoryCount() { return categoryRepository.countByIsActiveTrue(); }
    @Override public long getCategoryCountByLevel(Integer level) { return categoryRepository.countByLevelAndIsActiveTrue(level); }
    @Override public long getChildCategoryCount(Long parentId) { return categoryRepository.countByParentIdAndIsActiveTrue(parentId); }
    @Override public List<CategorySummaryDto> getCategoriesByLevel(Integer level) { return List.of(); }
    @Override public Integer getMaxCategoryLevel() { return 0; }
    @Override public void updateCategoryLevels(Long parentId) { }
    @Override public void updateDisplayOrder(Long categoryId, Integer newOrder) { }
    @Override public void reorderCategories(List<Long> categoryIds) { }
    @Override public void moveCategory(Long categoryId, Long newParentId) { }
    @Override public void moveCategoryUp(Long categoryId) { }
    @Override public void moveCategoryDown(Long categoryId) { }
    @Override public boolean hasChildren(Long categoryId) { return categoryRepository.existsByParentId(categoryId); }
    @Override public boolean hasProducts(Long categoryId) { return productCategoryRepository.countByCategoryId(categoryId) > 0; }
    @Override public boolean canBeDeleted(Long categoryId) { return !hasChildren(categoryId) && !hasProducts(categoryId); }
    @Override public List<CategorySummaryDto> getRelatedCategories(Long categoryId) { return List.of(); }
    @Override public List<CategoryResponseDto.CategoryBreadcrumb> getCategoryBreadcrumbs(Long categoryId) { return List.of(); }
    @Override public String getCategoryPathString(Long categoryId) { return ""; }
    @Override public void cleanupEmptyCategories() { }
    @Override public void rebuildCategoryTree() { }
    @Override public void validateCategoryIntegrity() { }
    @Override public List<CategorySummaryDto> getCategoriesByIds(List<Long> categoryIds) { return List.of(); }
    @Override public void bulkDeleteCategories(List<Long> categoryIds) { }
    @Override @CacheEvict(value = "categories", allEntries = true) public void clearCategoryCache() { }
    @Override @CacheEvict(value = "categories", key = "#categoryId") public void clearCategoryCache(Long categoryId) { }
    @Override public void refreshCategoryCache(Long categoryId) { }
    @Override public List<CategoryTreeDto> exportCategoryTree() { return List.of(); }
    @Override public void importCategoryTree(List<CategoryTreeDto> categoryTree) { }
    @Override public boolean existsById(Long id) { return categoryRepository.existsById(id); }
    @Override public boolean existsBySlug(String slug) { return categoryRepository.existsBySlug(slug); }
    @Override public boolean existsByParentId(Long parentId) { return categoryRepository.existsByParentId(parentId); }
}
