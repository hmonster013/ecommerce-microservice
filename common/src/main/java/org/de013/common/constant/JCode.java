package org.de013.common.constant;

public final class JCode {

    // Private constructor to prevent instantiation
    private JCode() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ========== Response Status ==========
    public static final String SUCCESS = "SUCCESS";
    public static final String ERROR = "ERROR";
    public static final String WARNING = "WARNING";
    public static final String INFO = "INFO";

    // ========== Business Logic Codes ==========
    public static final String OK = "0000";
    public static final String CREATED = "0001";
    public static final String UPDATED = "0002";
    public static final String DELETED = "0003";

    // ========== Client Error Codes (4xxx) ==========
    public static final String BAD_REQUEST = "4000";
    public static final String UNAUTHORIZED = "4001";
    public static final String FORBIDDEN = "4003";
    public static final String NOT_FOUND = "4004";
    public static final String METHOD_NOT_ALLOWED = "4005";
    public static final String CONFLICT = "4009";
    public static final String VALIDATION_ERROR = "4022";
    public static final String TOO_MANY_REQUESTS = "4029";

    // ========== Server Error Codes (5xxx) ==========
    public static final String INTERNAL_SERVER_ERROR = "5000";
    public static final String NOT_IMPLEMENTED = "5001";
    public static final String BAD_GATEWAY = "5002";
    public static final String SERVICE_UNAVAILABLE = "5003";
    public static final String GATEWAY_TIMEOUT = "5004";

    // ========== Business Error Codes (9xxx) ==========
    public static final String BUSINESS_ERROR = "9000";
    public static final String DATA_NOT_FOUND = "9001";
    public static final String DUPLICATE_DATA = "9002";
    public static final String INVALID_OPERATION = "9003";
    public static final String INSUFFICIENT_PERMISSION = "9004";
}
