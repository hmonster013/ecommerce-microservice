package org.de013.shoppingcart.documentation;

/**
 * API Error Codes Documentation
 * Comprehensive list of all error codes used in the Shopping Cart Service
 */
public class ApiErrorCodes {

    // ==================== CART ERRORS (1000-1999) ====================
    
    /**
     * Cart not found
     * HTTP Status: 404
     * Description: The requested cart does not exist or has been deleted
     */
    public static final String CART_NOT_FOUND = "CART_1001";
    
    /**
     * Cart access denied
     * HTTP Status: 403
     * Description: User does not have permission to access this cart
     */
    public static final String CART_ACCESS_DENIED = "CART_1002";
    
    /**
     * Cart is expired
     * HTTP Status: 410
     * Description: The cart has expired and is no longer accessible
     */
    public static final String CART_EXPIRED = "CART_1003";
    
    /**
     * Cart is already converted
     * HTTP Status: 409
     * Description: The cart has already been converted to an order
     */
    public static final String CART_ALREADY_CONVERTED = "CART_1004";
    
    /**
     * Cart item limit exceeded
     * HTTP Status: 400
     * Description: Maximum number of items per cart exceeded
     */
    public static final String CART_ITEM_LIMIT_EXCEEDED = "CART_1005";
    
    /**
     * Cart value limit exceeded
     * HTTP Status: 400
     * Description: Maximum cart value exceeded
     */
    public static final String CART_VALUE_LIMIT_EXCEEDED = "CART_1006";

    // ==================== CART ITEM ERRORS (2000-2999) ====================
    
    /**
     * Cart item not found
     * HTTP Status: 404
     * Description: The requested cart item does not exist
     */
    public static final String CART_ITEM_NOT_FOUND = "ITEM_2001";
    
    /**
     * Product not found
     * HTTP Status: 404
     * Description: The product does not exist in the catalog
     */
    public static final String PRODUCT_NOT_FOUND = "ITEM_2002";
    
    /**
     * Product out of stock
     * HTTP Status: 409
     * Description: The product is currently out of stock
     */
    public static final String PRODUCT_OUT_OF_STOCK = "ITEM_2003";
    
    /**
     * Insufficient stock
     * HTTP Status: 409
     * Description: Not enough stock available for the requested quantity
     */
    public static final String INSUFFICIENT_STOCK = "ITEM_2004";
    
    /**
     * Invalid quantity
     * HTTP Status: 400
     * Description: Quantity must be a positive integer
     */
    public static final String INVALID_QUANTITY = "ITEM_2005";
    
    /**
     * Quantity limit exceeded
     * HTTP Status: 400
     * Description: Maximum quantity per item exceeded
     */
    public static final String QUANTITY_LIMIT_EXCEEDED = "ITEM_2006";
    
    /**
     * Product price changed
     * HTTP Status: 409
     * Description: Product price has changed since it was added to cart
     */
    public static final String PRODUCT_PRICE_CHANGED = "ITEM_2007";
    
    /**
     * Product discontinued
     * HTTP Status: 410
     * Description: The product has been discontinued and is no longer available
     */
    public static final String PRODUCT_DISCONTINUED = "ITEM_2008";

    // ==================== AUTHENTICATION ERRORS (3000-3999) ====================
    
    /**
     * Authentication required
     * HTTP Status: 401
     * Description: Authentication is required to access this resource
     */
    public static final String AUTHENTICATION_REQUIRED = "AUTH_3001";
    
    /**
     * Invalid token
     * HTTP Status: 401
     * Description: The provided authentication token is invalid or expired
     */
    public static final String INVALID_TOKEN = "AUTH_3002";
    
    /**
     * Token expired
     * HTTP Status: 401
     * Description: The authentication token has expired
     */
    public static final String TOKEN_EXPIRED = "AUTH_3003";
    
    /**
     * Insufficient privileges
     * HTTP Status: 403
     * Description: User does not have sufficient privileges for this operation
     */
    public static final String INSUFFICIENT_PRIVILEGES = "AUTH_3004";
    
    /**
     * Invalid session
     * HTTP Status: 401
     * Description: The session is invalid or has expired
     */
    public static final String INVALID_SESSION = "AUTH_3005";
    
    /**
     * Guest session expired
     * HTTP Status: 401
     * Description: The guest session has expired
     */
    public static final String GUEST_SESSION_EXPIRED = "AUTH_3006";

    // ==================== VALIDATION ERRORS (4000-4999) ====================
    
    /**
     * Invalid request format
     * HTTP Status: 400
     * Description: The request format is invalid or malformed
     */
    public static final String INVALID_REQUEST_FORMAT = "VALID_4001";
    
    /**
     * Missing required field
     * HTTP Status: 400
     * Description: A required field is missing from the request
     */
    public static final String MISSING_REQUIRED_FIELD = "VALID_4002";
    
    /**
     * Invalid field value
     * HTTP Status: 400
     * Description: A field contains an invalid value
     */
    public static final String INVALID_FIELD_VALUE = "VALID_4003";
    
    /**
     * Invalid currency
     * HTTP Status: 400
     * Description: The specified currency is not supported
     */
    public static final String INVALID_CURRENCY = "VALID_4004";
    
    /**
     * Invalid price
     * HTTP Status: 400
     * Description: The price value is invalid or negative
     */
    public static final String INVALID_PRICE = "VALID_4005";

    // ==================== BUSINESS LOGIC ERRORS (5000-5999) ====================
    
    /**
     * Pricing calculation failed
     * HTTP Status: 500
     * Description: Failed to calculate pricing for the cart
     */
    public static final String PRICING_CALCULATION_FAILED = "BIZ_5001";
    
    /**
     * Tax calculation failed
     * HTTP Status: 500
     * Description: Failed to calculate tax for the cart
     */
    public static final String TAX_CALCULATION_FAILED = "BIZ_5002";
    
    /**
     * Shipping calculation failed
     * HTTP Status: 500
     * Description: Failed to calculate shipping cost
     */
    public static final String SHIPPING_CALCULATION_FAILED = "BIZ_5003";
    
    /**
     * Discount application failed
     * HTTP Status: 500
     * Description: Failed to apply discount to the cart
     */
    public static final String DISCOUNT_APPLICATION_FAILED = "BIZ_5004";
    
    /**
     * Invalid discount code
     * HTTP Status: 400
     * Description: The discount code is invalid or expired
     */
    public static final String INVALID_DISCOUNT_CODE = "BIZ_5005";
    
    /**
     * Promotion not applicable
     * HTTP Status: 400
     * Description: The promotion is not applicable to this cart
     */
    public static final String PROMOTION_NOT_APPLICABLE = "BIZ_5006";

    // ==================== EXTERNAL SERVICE ERRORS (6000-6999) ====================
    
    /**
     * Product service unavailable
     * HTTP Status: 503
     * Description: Product catalog service is temporarily unavailable
     */
    public static final String PRODUCT_SERVICE_UNAVAILABLE = "EXT_6001";
    
    /**
     * User service unavailable
     * HTTP Status: 503
     * Description: User service is temporarily unavailable
     */
    public static final String USER_SERVICE_UNAVAILABLE = "EXT_6002";
    
    /**
     * Payment service unavailable
     * HTTP Status: 503
     * Description: Payment service is temporarily unavailable
     */
    public static final String PAYMENT_SERVICE_UNAVAILABLE = "EXT_6003";
    
    /**
     * External service timeout
     * HTTP Status: 504
     * Description: External service request timed out
     */
    public static final String EXTERNAL_SERVICE_TIMEOUT = "EXT_6004";
    
    /**
     * External service error
     * HTTP Status: 502
     * Description: External service returned an error
     */
    public static final String EXTERNAL_SERVICE_ERROR = "EXT_6005";

    // ==================== SYSTEM ERRORS (7000-7999) ====================
    
    /**
     * Database error
     * HTTP Status: 500
     * Description: Database operation failed
     */
    public static final String DATABASE_ERROR = "SYS_7001";
    
    /**
     * Cache error
     * HTTP Status: 500
     * Description: Cache operation failed
     */
    public static final String CACHE_ERROR = "SYS_7002";
    
    /**
     * Configuration error
     * HTTP Status: 500
     * Description: System configuration error
     */
    public static final String CONFIGURATION_ERROR = "SYS_7003";
    
    /**
     * Rate limit exceeded
     * HTTP Status: 429
     * Description: API rate limit exceeded
     */
    public static final String RATE_LIMIT_EXCEEDED = "SYS_7004";
    
    /**
     * Service unavailable
     * HTTP Status: 503
     * Description: Service is temporarily unavailable
     */
    public static final String SERVICE_UNAVAILABLE = "SYS_7005";
    
    /**
     * Internal server error
     * HTTP Status: 500
     * Description: An unexpected internal server error occurred
     */
    public static final String INTERNAL_SERVER_ERROR = "SYS_7006";

    // ==================== PERFORMANCE ERRORS (8000-8999) ====================
    
    /**
     * Request timeout
     * HTTP Status: 408
     * Description: Request processing timed out
     */
    public static final String REQUEST_TIMEOUT = "PERF_8001";
    
    /**
     * Resource exhausted
     * HTTP Status: 503
     * Description: System resources are exhausted
     */
    public static final String RESOURCE_EXHAUSTED = "PERF_8002";
    
    /**
     * Performance degraded
     * HTTP Status: 503
     * Description: System performance is degraded
     */
    public static final String PERFORMANCE_DEGRADED = "PERF_8003";

    // ==================== ERROR CODE UTILITIES ====================
    
    /**
     * Get error category from error code
     */
    public static String getErrorCategory(String errorCode) {
        if (errorCode == null || errorCode.length() < 4) {
            return "UNKNOWN";
        }
        
        String prefix = errorCode.substring(0, errorCode.indexOf('_'));
        return switch (prefix) {
            case "CART" -> "Cart Management";
            case "ITEM" -> "Cart Items";
            case "AUTH" -> "Authentication";
            case "VALID" -> "Validation";
            case "BIZ" -> "Business Logic";
            case "EXT" -> "External Services";
            case "SYS" -> "System";
            case "PERF" -> "Performance";
            default -> "Unknown";
        };
    }
    
    /**
     * Check if error code indicates a client error (4xx)
     */
    public static boolean isClientError(String errorCode) {
        return errorCode != null && (
            errorCode.startsWith("CART_1005") || errorCode.startsWith("CART_1006") ||
            errorCode.startsWith("ITEM_2005") || errorCode.startsWith("ITEM_2006") ||
            errorCode.startsWith("VALID_") ||
            errorCode.startsWith("BIZ_5005") || errorCode.startsWith("BIZ_5006")
        );
    }
    
    /**
     * Check if error code indicates a server error (5xx)
     */
    public static boolean isServerError(String errorCode) {
        return errorCode != null && (
            errorCode.startsWith("BIZ_50") ||
            errorCode.startsWith("EXT_") ||
            errorCode.startsWith("SYS_") ||
            errorCode.startsWith("PERF_")
        );
    }
}
