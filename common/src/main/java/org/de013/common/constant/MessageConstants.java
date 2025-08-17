package org.de013.common.constant;

public final class MessageConstants {

    // Private constructor to prevent instantiation
    private MessageConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ========== Success Messages ==========
    public static final String SUCCESS = "Operation completed successfully";
    public static final String CREATED = "Resource created successfully";
    public static final String UPDATED = "Resource updated successfully";
    public static final String DELETED = "Resource deleted successfully";
    public static final String RETRIEVED = "Resource retrieved successfully";

    // ========== Authentication Messages ==========
    public static final String LOGIN_SUCCESS = "Login successful";
    public static final String LOGOUT_SUCCESS = "Logout successful";
    public static final String REGISTER_SUCCESS = "User registered successfully";
    public static final String TOKEN_REFRESHED = "Token refreshed successfully";

    // ========== Error Messages ==========
    public static final String ERROR = "An error occurred";
    public static final String VALIDATION_ERROR = "Validation failed";
    public static final String UNAUTHORIZED = "Unauthorized access";
    public static final String FORBIDDEN = "Access forbidden";
    public static final String NOT_FOUND = "Resource not found";
    public static final String CONFLICT = "Resource already exists";
    public static final String INTERNAL_ERROR = "Internal server error";
    public static final String BAD_REQUEST = "Invalid request";
}

