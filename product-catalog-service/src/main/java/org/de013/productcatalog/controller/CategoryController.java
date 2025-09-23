package org.de013.productcatalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.de013.common.constant.ApiPaths;
import org.de013.common.controller.BaseController;

import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.dto.category.*;
import org.de013.productcatalog.dto.product.ProductSummaryDto;
import org.de013.productcatalog.service.CategoryService;
import org.de013.productcatalog.service.ProductService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(ApiPaths.CATEGORIES)
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Category management API")
public class CategoryController extends BaseController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CategoryController.class);

    private final CategoryService categoryService;
    private final ProductService productService;

    @Operation(summary = "Get all categories", description = "Retrieve paginated list of categories")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Categories retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    })
    @GetMapping
    public ResponseEntity<org.de013.common.dto.ApiResponse<PageResponse<CategoryResponseDto>>> getAllCategories(
            @Parameter(description = "Pagination and sorting parameters")
            @PageableDefault(size = 20, sort = "displayOrder", direction = Sort.Direction.ASC) Pageable pageable,
            
            @Parameter(description = "Show only active categories")
            @RequestParam(required = false, defaultValue = "true") Boolean activeOnly) {
        
        log.info("Getting categories with activeOnly: {}", activeOnly);

        PageResponse<CategoryResponseDto> categories = categoryService.getAllCategories(pageable);
        return ok(categories);
    }

    @Operation(summary = "Get category by ID", description = "Retrieve detailed category information by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Category found"),
        @ApiResponse(responseCode = "404", description = "Category not found")
    })
    @GetMapping(ApiPaths.ID_PARAM)
    public ResponseEntity<org.de013.common.dto.ApiResponse<CategoryResponseDto>> getCategoryById(
            @Parameter(description = "Category ID", required = true)
            @PathVariable Long id) {

        log.info("Getting category by ID: {}", id);

        CategoryResponseDto category = categoryService.getCategoryById(id);
        return ok(category);
    }

    @Operation(summary = "Get category by slug", description = "Retrieve detailed category information by slug")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Category found"),
        @ApiResponse(responseCode = "404", description = "Category not found")
    })
    @GetMapping(ApiPaths.SLUG_PARAM)
    public ResponseEntity<org.de013.common.dto.ApiResponse<CategoryResponseDto>> getCategoryBySlug(
            @Parameter(description = "Category slug", required = true)
            @PathVariable String slug) {

        log.info("Getting category by slug: {}", slug);

        CategoryResponseDto category = categoryService.getCategoryBySlug(slug);
        return ok(category);
    }

    @Operation(summary = "[ADMIN] Create new category", description = "Create a new category")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Category created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid category data"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<CategoryResponseDto>> createCategory(
            @Parameter(description = "Category creation data", required = true)
            @Valid @RequestBody CategoryCreateDto createDto) {

        log.info("Creating new category with name: {}", createDto.getName());

        CategoryResponseDto category = categoryService.createCategory(createDto);
        return created(category, "Category created successfully");
    }

    @Operation(summary = "[ADMIN] Update category", description = "Update existing category")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Category updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid category data"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Category not found")
    })
    @PutMapping(ApiPaths.ID_PARAM)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<CategoryResponseDto>> updateCategory(
            @Parameter(description = "Category ID", required = true)
            @PathVariable Long id,

            @Parameter(description = "Category update data", required = true)
            @Valid @RequestBody CategoryUpdateDto updateDto) {

        log.info("Updating category with ID: {}", id);

        CategoryResponseDto category = categoryService.updateCategory(id, updateDto);
        return updated(category, "Category updated successfully");
    }

    @Operation(summary = "[ADMIN] Delete category", description = "Delete category")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Category deleted successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Category not found"),
        @ApiResponse(responseCode = "409", description = "Category has children or products")
    })
    @DeleteMapping(ApiPaths.ID_PARAM)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<String>> deleteCategory(
            @Parameter(description = "Category ID", required = true)
            @PathVariable Long id) {

        log.info("Deleting category with ID: {}", id);

        categoryService.deleteCategory(id);
        return deleted("Category deleted successfully");
    }

    @Operation(summary = "Get products in category", description = "Retrieve products belonging to a specific category")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Products retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Category not found")
    })
    @GetMapping(ApiPaths.ID_PARAM + ApiPaths.PRODUCTS)
    public ResponseEntity<org.de013.common.dto.ApiResponse<PageResponse<ProductSummaryDto>>> getProductsInCategory(
            @Parameter(description = "Category ID", required = true)
            @PathVariable Long id,
            
            @Parameter(description = "Pagination and sorting parameters")
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        
        log.info("Getting products in category ID: {}", id);
        
        PageResponse<ProductSummaryDto> products = productService.getProductsByCategory(id, pageable);
        return ok(products);
    }

    @Operation(summary = "Get category tree", description = "Retrieve hierarchical category tree structure")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Category tree retrieved successfully")
    })
    @GetMapping(ApiPaths.TREE)
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<CategoryTreeDto>>> getCategoryTree(
            @Parameter(description = "Maximum depth level to retrieve")
            @RequestParam(required = false) @Min(0) Integer maxLevel) {

        log.info("Getting category tree with maxLevel: {}", maxLevel);

        List<CategoryTreeDto> categoryTree = categoryService.getCategoryTree(maxLevel);

        return ok(categoryTree);
    }

    @Operation(summary = "Get root categories", description = "Retrieve top-level categories")
    @GetMapping(ApiPaths.ROOT)
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<CategorySummaryDto>>> getRootCategories() {
        log.info("Getting root categories");

        List<CategorySummaryDto> categories = categoryService.getRootCategories();
        return ok(categories);
    }

    @Operation(summary = "Get child categories", description = "Retrieve child categories of a parent category")
    @GetMapping(ApiPaths.ID_PARAM + ApiPaths.CHILDREN)
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<CategorySummaryDto>>> getChildCategories(
            @Parameter(description = "Parent category ID", required = true)
            @PathVariable Long id) {
        
        log.info("Getting child categories for parent ID: {}", id);
        
        List<CategorySummaryDto> children = categoryService.getChildCategories(id);
        return ok(children);
    }

    @Operation(summary = "Get category path", description = "Get breadcrumb path for a category")
    @GetMapping(ApiPaths.ID_PARAM + ApiPaths.PATH)
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<CategorySummaryDto>>> getCategoryPath(
            @Parameter(description = "Category ID", required = true)
            @PathVariable Long id) {

        log.info("Getting category path for ID: {}", id);

        List<CategorySummaryDto> path = categoryService.getCategoryPath(id);
        return ok(path);
    }

    @Operation(summary = "Search categories", description = "Search categories by name")
    @GetMapping(ApiPaths.SEARCH)
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<CategorySummaryDto>>> searchCategories(
            @Parameter(description = "Search query", required = true)
            @RequestParam String q) {

        log.info("Searching categories with query: {}", q);

        List<CategorySummaryDto> categories = categoryService.searchCategories(q);
        return ok(categories);
    }

    // Admin endpoints
    @Operation(summary = "[ADMIN] Set category active status", description = "Activate or deactivate category")
    @PutMapping(ApiPaths.ID_PARAM + ApiPaths.ACTIVE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<CategoryResponseDto>> setActiveStatus(
            @PathVariable Long id,
            @RequestParam Boolean active) {

        log.info("Setting active status for category ID: {} to: {}", id, active);

        CategoryResponseDto category = categoryService.updateCategoryStatus(id, active);

        return updated(category, "Category status updated successfully");
    }

    @Operation(summary = "[ADMIN] Move category", description = "Move category to different parent")
    @PutMapping(ApiPaths.ID_PARAM + ApiPaths.MOVE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.de013.common.dto.ApiResponse<CategoryResponseDto>> moveCategory(
            @PathVariable Long id,
            @RequestParam(required = false) Long newParentId) {

        log.info("Moving category ID: {} to new parent ID: {}", id, newParentId);

        categoryService.moveCategory(id, newParentId);
        CategoryResponseDto category = categoryService.getCategoryById(id);

        return updated(category, "Category moved successfully");
    }
}
