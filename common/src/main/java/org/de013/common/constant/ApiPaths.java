package org.de013.common.constant;

public final class ApiPaths {
    private ApiPaths() {}

    // Cấp 1
    public static final String API = "/api";

    // Common versions (optional convenience)
    public static final String V1 = "/v1";
    public static final String V2 = "/v2";

    // Resource roots (optional convenience)
    public static final String USERS = "/users";
    public static final String AUTH = "/auth";
    public static final String PRODUCTS = "/products";
    public static final String HEALTH = "/health";
    public static final String CART = "/cart";
    public static final String ORDERS = "/orders";
    public static final String PAYMENTS = "/payments";
    public static final String NOTIFICATIONS = "/notifications";

    // Common endpoints
    public static final String REGISTER = "/register";
    public static final String LOGIN = "/login";
    public static final String REFRESH = "/refresh";
    public static final String LOGOUT = "/logout";
    public static final String USERNAME = "/username";
    public static final String ID_PARAM = "/{id}";
    public static final String USERNAME_PARAM = "/username/{username}";
}

