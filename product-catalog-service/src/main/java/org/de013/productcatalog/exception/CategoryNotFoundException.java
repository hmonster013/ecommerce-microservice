package org.de013.productcatalog.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested category is not found.
 */
public class CategoryNotFoundException extends BaseBusinessException {

    private static final String ERROR_CODE = "CATEGORY_NOT_FOUND";

    public CategoryNotFoundException(Long categoryId) {
        super(
            String.format("Category with ID %d not found", categoryId),
            ERROR_CODE,
            HttpStatus.NOT_FOUND,
            categoryId
        );
    }

    public CategoryNotFoundException(String slug) {
        super(
            String.format("Category with slug '%s' not found", slug),
            ERROR_CODE,
            HttpStatus.NOT_FOUND,
            slug
        );
    }

    public CategoryNotFoundException(String message, Object... args) {
        super(message, ERROR_CODE, HttpStatus.NOT_FOUND, args);
    }

    public CategoryNotFoundException(String message, Throwable cause) {
        super(message, cause, ERROR_CODE, HttpStatus.NOT_FOUND);
    }
}
