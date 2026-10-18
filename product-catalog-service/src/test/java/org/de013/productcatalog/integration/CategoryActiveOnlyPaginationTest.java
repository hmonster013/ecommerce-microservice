package org.de013.productcatalog.integration;

import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.dto.category.CategoryResponseDto;
import org.de013.productcatalog.entity.Category;
import org.de013.productcatalog.exception.InvalidCategoryHierarchyException;
import org.de013.productcatalog.repository.CategoryRepository;
import org.de013.productcatalog.repository.ProductCategoryRepository;
import org.de013.productcatalog.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CategoryActiveOnlyPaginationTest {

    @Autowired private CategoryService categoryService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductCategoryRepository productCategoryRepository;
    @MockBean(name = "cacheManager") private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        CacheManager localCaches = new ConcurrentMapCacheManager();
        when(cacheManager.getCache(anyString())).thenAnswer(call -> localCaches.getCache(call.getArgument(0)));

        productCategoryRepository.deleteAll();
        categoryRepository.deleteAll();

        // Create 10 active categories and 5 inactive categories
        for (int i = 1; i <= 10; i++) {
            categoryRepository.save(Category.builder()
                    .name("Active Cat " + i)
                    .slug("active-cat-" + i)
                    .isActive(true)
                    .displayOrder(i)
                    .build());
        }

        for (int i = 1; i <= 5; i++) {
            categoryRepository.save(Category.builder()
                    .name("Inactive Cat " + i)
                    .slug("inactive-cat-" + i)
                    .isActive(false)
                    .displayOrder(100 + i)
                    .build());
        }
    }

    @Test
    void testActiveOnly_True_ReflectsOnlyActiveTotalsInDB() {
        Pageable pageable = PageRequest.of(0, 20);
        PageResponse<CategoryResponseDto> response = categoryService.getAllCategories(true, pageable);

        assertEquals(10, response.getTotalElements());
        assertEquals(10, response.getContent().size());
        for (CategoryResponseDto cat : response.getContent()) {
            assertTrue(Boolean.TRUE.equals(cat.getIsActive()), "Should only contain active categories");
        }
    }

    @Test
    void testActiveOnly_False_ReflectsAllCategoriesInDB() {
        Pageable pageable = PageRequest.of(0, 20);
        PageResponse<CategoryResponseDto> response = categoryService.getAllCategories(false, pageable);

        assertEquals(15, response.getTotalElements());
        assertEquals(15, response.getContent().size());
    }

    @Test
    void testDeleteCategory_BlockedWhenOnlyInactiveChildrenExist() {
        Category parent = categoryRepository.save(Category.builder()
                .name("Parent Cat")
                .slug("parent-cat")
                .isActive(true)
                .build());

        // Add an INACTIVE child to this parent
        categoryRepository.save(Category.builder()
                .name("Inactive Child")
                .slug("inactive-child")
                .parent(parent)
                .isActive(false)
                .build());

        // Deleting parent must be blocked with InvalidCategoryHierarchyException
        InvalidCategoryHierarchyException ex = assertThrows(
                InvalidCategoryHierarchyException.class,
                () -> categoryService.deleteCategory(parent.getId())
        );

        assertTrue(ex.getMessage().contains("child categories"), "Error message should mention child categories");
    }
}
