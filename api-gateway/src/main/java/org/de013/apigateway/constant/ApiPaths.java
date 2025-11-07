package org.de013.apigateway.constant;

public final class ApiPaths {
    private ApiPaths() {}

    // Gateway Base Paths
    public static final String FALLBACK = "/fallback";
    public static final String API = "/api";
    public static final String API_DOCS = "/v3/api-docs";
    
    // Path Variables
    public static final String SERVICE_NAME_PARAM = "/{serviceName}";
    
    // Regex Patterns for Rewrite
    public static final String REMAINING_PATH_REGEX = "(?<remaining>.*)";
    public static final String REMAINING_PATH_REPLACEMENT = "/${remaining}";
    
    // Service Names
    public static final String USER_SERVICE = "user-service";
    public static final String PRODUCT_CATALOG_SERVICE = "product-catalog-service";
    public static final String SHOPPING_CART_SERVICE = "shopping-cart-service";
    public static final String ORDER_SERVICE = "order-service";
    public static final String PAYMENT_SERVICE = "payment-service";
    public static final String NOTIFICATION_SERVICE = "notification-service";
    
    // Load Balanced URIs
    public static final String LB_USER_SERVICE = "lb://" + USER_SERVICE;
    public static final String LB_PRODUCT_CATALOG_SERVICE = "lb://" + PRODUCT_CATALOG_SERVICE;
    public static final String LB_SHOPPING_CART_SERVICE = "lb://" + SHOPPING_CART_SERVICE;
    public static final String LB_ORDER_SERVICE = "lb://" + ORDER_SERVICE;
    public static final String LB_PAYMENT_SERVICE = "lb://" + PAYMENT_SERVICE;
    public static final String LB_NOTIFICATION_SERVICE = "lb://" + NOTIFICATION_SERVICE;
    
    // Gateway Route Paths (Pattern: /api/{service-name}/**)
    public static final String ROUTE_USER_SERVICE = API + "/" + USER_SERVICE + "/**";
    public static final String ROUTE_PRODUCT_CATALOG_SERVICE = API + "/" + PRODUCT_CATALOG_SERVICE + "/**";
    public static final String ROUTE_SHOPPING_CART_SERVICE = API + "/" + SHOPPING_CART_SERVICE + "/**";
    public static final String ROUTE_ORDER_SERVICE = API + "/" + ORDER_SERVICE + "/**";
    public static final String ROUTE_PAYMENT_SERVICE = API + "/" + PAYMENT_SERVICE + "/**";
    public static final String ROUTE_NOTIFICATION_SERVICE = API + "/" + NOTIFICATION_SERVICE + "/**";
    
    // Rewrite Path Patterns (Pattern: /api/{service-name}/(?<remaining>.*))
    public static final String REWRITE_USER_SERVICE = API + "/" + USER_SERVICE + "/" + REMAINING_PATH_REGEX;
    public static final String REWRITE_PRODUCT_CATALOG_SERVICE = API + "/" + PRODUCT_CATALOG_SERVICE + "/" + REMAINING_PATH_REGEX;
    public static final String REWRITE_SHOPPING_CART_SERVICE = API + "/" + SHOPPING_CART_SERVICE + "/" + REMAINING_PATH_REGEX;
    public static final String REWRITE_ORDER_SERVICE = API + "/" + ORDER_SERVICE + "/" + REMAINING_PATH_REGEX;
    public static final String REWRITE_PAYMENT_SERVICE = API + "/" + PAYMENT_SERVICE + "/" + REMAINING_PATH_REGEX;
    public static final String REWRITE_NOTIFICATION_SERVICE = API + "/" + NOTIFICATION_SERVICE + "/" + REMAINING_PATH_REGEX;
    
    // API Documentation Paths
    public static final String DOCS_USER_SERVICE = API + "/" + USER_SERVICE + API_DOCS;
    public static final String DOCS_PRODUCT_CATALOG_SERVICE = API + "/" + PRODUCT_CATALOG_SERVICE + API_DOCS;
    public static final String DOCS_SHOPPING_CART_SERVICE = API + "/" + SHOPPING_CART_SERVICE + API_DOCS;
    public static final String DOCS_ORDER_SERVICE = API + "/" + ORDER_SERVICE + API_DOCS;
    public static final String DOCS_PAYMENT_SERVICE = API + "/" + PAYMENT_SERVICE + API_DOCS;
    public static final String DOCS_NOTIFICATION_SERVICE = API + "/" + NOTIFICATION_SERVICE + API_DOCS;
    
    // Circuit Breaker Names
    public static final String CB_USER_SERVICE = "userServiceCircuitBreaker";
    public static final String CB_PRODUCT_SERVICE = "productServiceCircuitBreaker";
    public static final String CB_CART_SERVICE = "cartServiceCircuitBreaker";
    public static final String CB_ORDER_SERVICE = "orderServiceCircuitBreaker";
    public static final String CB_PAYMENT_SERVICE = "paymentServiceCircuitBreaker";
    public static final String CB_NOTIFICATION_SERVICE = "notificationServiceCircuitBreaker";
    
    // Fallback URIs (forward URIs for circuit breaker)
    public static final String FALLBACK_USER_SERVICE = "forward:" + FALLBACK + "/" + USER_SERVICE;
    public static final String FALLBACK_PRODUCT_CATALOG_SERVICE = "forward:" + FALLBACK + "/" + PRODUCT_CATALOG_SERVICE;
    public static final String FALLBACK_SHOPPING_CART_SERVICE = "forward:" + FALLBACK + "/" + SHOPPING_CART_SERVICE;
    public static final String FALLBACK_ORDER_SERVICE = "forward:" + FALLBACK + "/" + ORDER_SERVICE;
    public static final String FALLBACK_PAYMENT_SERVICE = "forward:" + FALLBACK + "/" + PAYMENT_SERVICE;
    public static final String FALLBACK_NOTIFICATION_SERVICE = "forward:" + FALLBACK + "/" + NOTIFICATION_SERVICE;
}
