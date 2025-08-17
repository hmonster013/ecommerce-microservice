package org.de013.productcatalog.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when an invalid category hierarchy operation is attempted.
 */
public class InvalidCategoryHierarchyException extends BaseBusinessException {

    private static final String ERROR_CODE = "INVALID_CATEGORY_HIERARCHY";

    public InvalidCategoryHierarchyException(String message) {
        super(message, ERROR_CODE, HttpStatus.BAD_REQUEST);
    }

    public InvalidCategoryHierarchyException(Long categoryId, Long parentId) {
        super(
            String.format("Cannot set category %d as parent of category %d - would create circular reference", 
                         parentId, categoryId),
            ERROR_CODE,
            HttpStatus.BAD_REQUEST,
            categoryId, parentId
        );
    }

    public InvalidCategoryHierarchyException(String message, Object... args) {
        super(message, ERROR_CODE, HttpStatus.BAD_REQUEST, args);
    }

    public InvalidCategoryHierarchyException(String message, Throwable cause) {
        super(message, cause, ERROR_CODE, HttpStatus.BAD_REQUEST);
    }

    public static InvalidCategoryHierarchyException circularReference(Long categoryId, Long parentId) {
        return new InvalidCategoryHierarchyException(categoryId, parentId);
    }

    public static InvalidCategoryHierarchyException maxDepthExceeded(int maxDepth) {
        return new InvalidCategoryHierarchyException(
            String.format("Category hierarchy depth cannot exceed %d levels", maxDepth),
            maxDepth
        );
    }

    public static InvalidCategoryHierarchyException cannotDeleteWithChildren(Long categoryId, int childCount) {
        return new InvalidCategoryHierarchyException(
            String.format("Cannot delete category %d - it has %d child categories", categoryId, childCount),
            categoryId, childCount
        );
    }

    public static InvalidCategoryHierarchyException cannotDeleteWithProducts(Long categoryId, int productCount) {
        return new InvalidCategoryHierarchyException(
            String.format("Cannot delete category %d - it has %d products", categoryId, productCount),
            categoryId, productCount
        );
    }
}
