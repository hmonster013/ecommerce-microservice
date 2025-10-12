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
import org.de013.productcatalog.mapper.CategoryMapper;
import org.de013.productcatalog.util.SlugUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final CategoryMapper categoryMapper;

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
        return categoryMapper.toCategoryResponseDto(category);
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
        return categoryMapper.toCategoryResponseDto(category);
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
        return categoryMapper.toCategoryResponseDto(category);
    }

    @Override
    @Cacheable(value = "categories", key = "#slug")
    public CategoryResponseDto getCategoryBySlug(String slug) {
        log.debug("Getting category by slug: {}", slug);
        
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new CategoryNotFoundException(slug));
        
        return categoryMapper.toCategoryResponseDto(category);
    }

    @Override
    @Cacheable(value = "categories", key = "'all:' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public PageResponse<CategoryResponseDto> getAllCategories(Pageable pageable) {
        log.debug("Getting all categories with pagination: {}", pageable);
        
        Page<Category> categories = categoryRepository.findAll(pageable);
        List<CategoryResponseDto> content = categories.getContent().stream()
                .map(categoryMapper::toCategoryResponseDto)
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
                .map(categoryMapper::toCategorySummaryDto)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "categories", key = "'all_active_ordered'")
    public List<CategorySummaryDto> getAllActiveCategoriesOrdered() {
        log.debug("Getting all active categories ordered");
        
        List<Category> categories = categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        return categories.stream()
                .map(categoryMapper::toCategorySummaryDto)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "categories", key = "'tree_level_' + #maxLevel")
    public List<CategoryTreeDto> getCategoryTree(Integer maxLevel) {
        log.debug("Getting category tree with max level: {}", maxLevel);

        // Get all root categories and build tree with level limit
        List<Category> rootCategories = categoryRepository.findCategoriesUpToLevel();
        List<CategoryTreeDto> treeDtos = rootCategories.stream()
                .map(category -> categoryMapper.toCategoryTreeDto(category))
                .collect(Collectors.toList());

        // Apply max level filtering to each tree
        treeDtos.forEach(treeDto -> buildCategoryTreeWithMaxLevel(treeDto, maxLevel));

        return treeDtos;
    }

    @Override
    @Cacheable(value = "categories", key = "'root'")
    public List<CategorySummaryDto> getRootCategories() {
        log.debug("Getting root categories");
        
        List<Category> categories = categoryRepository.findByParentIsNullAndIsActiveTrueOrderByDisplayOrderAsc();
        return categories.stream()
                .map(categoryMapper::toCategorySummaryDto)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "categories", key = "'children_' + #parentId")
    public List<CategorySummaryDto> getChildCategories(Long parentId) {
        log.debug("Getting child categories for parent ID: {}", parentId);
        
        List<Category> categories = categoryRepository.findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(parentId);
        return categories.stream()
                .map(categoryMapper::toCategorySummaryDto)
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
        CategoryTreeDto treeDto = categoryMapper.toCategoryTreeDto(category);

        List<Category> children = categoryRepository.findChildCategories(category.getId());
        if (!children.isEmpty()) {
            List<CategoryTreeDto> childrenDtos = children.stream()
                    .map(this::buildCategoryTreeRecursive)
                    .collect(Collectors.toList());
            treeDto.setChildren(childrenDtos);
        }

        return treeDto;
    }

    private void buildCategoryTreeWithMaxLevel(CategoryTreeDto treeDto, Integer maxLevel) {
        if (maxLevel == null) {
            return;
        }

        if (treeDto.getLevel() >= maxLevel) {
            treeDto.setChildren(null);
            return;
        } else if (treeDto.getChildren() != null && !treeDto.getChildren().isEmpty()) {
            List<CategoryTreeDto> children = treeDto.getChildren();
            for (CategoryTreeDto child : children) {
                buildCategoryTreeWithMaxLevel(child, maxLevel);
            }
        }
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

    private void updateDescendantLevels(Long parentCategoryId, Integer newParentLevel) {
        log.debug("Updating descendant levels for category ID: {} with new parent level: {}",
                parentCategoryId, newParentLevel);

        // Get all direct children
        List<Category> children = categoryRepository.findByParentId(parentCategoryId);

        for (Category child : children) {
            // Update child level
            int newChildLevel = newParentLevel + 1;
            child.setLevel(newChildLevel);
            categoryRepository.save(child);

            // Recursively update grandchildren
            updateDescendantLevels(child.getId(), newChildLevel);
        }

        log.debug("Updated levels for {} direct children of category ID: {}",
                children.size(), parentCategoryId);
    }

    @Override
    @Cacheable(value = "categories", key = "'path_' + #categoryId")
    public List<CategorySummaryDto> getCategoryPath(Long categoryId) {
        log.debug("Getting category path for ID: {}", categoryId);

        // Validate category exists
        if (!categoryRepository.existsById(categoryId)) {
            throw new CategoryNotFoundException(categoryId);
        }

        // Get path using recursive query
        List<Object[]> pathData = categoryRepository.findCategoryPath(categoryId);

        // Convert Object[] to CategorySummaryDto using mapper
        return pathData.stream()
                .map(categoryMapper::fromPathQueryResult)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "categories", key = "'search_' + (#query != null ? #query : 'all')")
    public List<CategorySummaryDto> searchCategories(String query) {
        log.debug("Searching categories with query: {}", query);

        // Repository handles both empty and non-empty queries
        List<Category> categories = categoryRepository.searchCategories(query);

        log.debug("Found {} categories matching query: {}", categories.size(), query);

        // Convert to DTOs and return
        return categories.stream()
                .map(categoryMapper::toCategorySummaryDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = "categories", key = "#id")
    public CategoryResponseDto updateCategoryStatus(Long id, boolean isActive) {
        Category category = findCategoryById(id);
        category.setIsActive(isActive);
        category = categoryRepository.save(category);

        return categoryMapper.toCategoryResponseDto(category);
    }

    @Override
    public long getTotalCategoryCount() {
        return categoryRepository.count();
    }

    @Override
    public long getActiveCategoryCount() {
        return categoryRepository.countByIsActiveTrue();
    }

    @Override
    public long getCategoryCountByLevel(Integer level) {
        return categoryRepository.countByLevelAndIsActiveTrue(level);
    }

    @Override
    public long getChildCategoryCount(Long parentId) {
        return categoryRepository.countByParentIdAndIsActiveTrue(parentId);
    }

    @Override
    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public void moveCategory(Long categoryId, Long newParentId) {
        // Validate category exists
        Category category = findCategoryById(categoryId);

        // Validate new parent if provided
        if (newParentId != null) {
            validateCategoryHierarchy(newParentId, categoryId);
        }

        // Get old parent for comparison
        Category oldParent = category.getParent();
        Long oldParentId = oldParent != null ? oldParent.getId() : null;

        // Check if actually moving to a different parent
        if (Objects.equals(oldParentId, newParentId)) {
            log.debug("Category {} is already under parent {}, no move needed", categoryId, newParentId);
            return;
        }

        // Update category parent and level
        updateCategoryParent(category, newParentId);

        // Update display order - place at end of siblings
        Integer newDisplayOrder = categoryRepository.findMaxDisplayOrderBySiblings(newParentId) + 1;
        category.setDisplayOrder(newDisplayOrder);

        // Save the category
        categoryRepository.save(category);

        // Update levels for all descendants recursively
        updateDescendantLevels(categoryId, category.getLevel());
    }

    @Override
    public boolean hasChildren(Long categoryId) {
        return categoryRepository.existsByParentId(categoryId);
    }

    @Override
    public boolean hasProducts(Long categoryId) {
        return productCategoryRepository.countByCategoryId(categoryId) > 0;
    }

    @Override
    public boolean canBeDeleted(Long categoryId) {
        return !hasChildren(categoryId) && !hasProducts(categoryId);
    }

    @Override
    @CacheEvict(value = "categories", allEntries = true)
    public void clearCategoryCache() {
    }

    @Override
    @CacheEvict(value = "categories", key = "#categoryId")
    public void clearCategoryCache(Long categoryId) {
    }

    @Override
    public boolean existsById(Long id) {
        return categoryRepository.existsById(id);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return categoryRepository.existsBySlug(slug);
    }

    @Override
    public boolean existsByParentId(Long parentId) {
        return categoryRepository.existsByParentId(parentId);
    }
}
