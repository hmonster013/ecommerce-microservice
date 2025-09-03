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
    public static final String CATEGORIES = "/categories";
    public static final String HEALTH = "/health";
    public static final String CART = "/cart";
    public static final String CARTS = "/carts";
    public static final String CART_ITEMS = "/cart-items";
    public static final String ORDERS = "/orders";
    public static final String PAYMENTS = "/payments";
    public static final String NOTIFICATIONS = "/notifications";

    // Common endpoints
    public static final String REGISTER = "/register";
    public static final String LOGIN = "/login";
    public static final String REFRESH = "/refresh";
    public static final String LOGOUT = "/logout";
    public static final String PROFILE = "/profile";
    public static final String CHANGE_PASSWORD = "/change-password";
    public static final String USERNAME = "/username";
    public static final String ID_PARAM = "/{id}";
    public static final String CART_ID_PARAM = "/{cartId}";
    public static final String ITEM_ID_PARAM = "/{itemId}";
    public static final String PRODUCT_ID_PARAM = "/{productId}";
    public static final String ORDER_ID_PARAM = "/{orderId}";
    public static final String USER_ID_PARAM = "/{userId}";
    public static final String ORDER_NUMBER_PARAM = "/{orderNumber}";
    public static final String USERNAME_PARAM = "/username/{username}";
    public static final String LEGACY = "/legacy";
    public static final String SEARCH = "/search";
    public static final String ENABLE = "/enable";
    public static final String DISABLE = "/disable";

    // Cart specific endpoints
    public static final String VALIDATE = "/validate";
    public static final String ACTIVITY = "/activity";
    public static final String QUANTITY = "/quantity";
    public static final String GIFT = "/gift";
    public static final String CLEAR = "/clear";
    public static final String COUPON = "/coupon";
    public static final String CHECKOUT = "/checkout";
    public static final String PREPARE = "/prepare";

    // Order specific endpoints
    public static final String MY_ORDERS = "/my-orders";
    public static final String NUMBER = "/number";
    public static final String USER = "/user";
    public static final String STATUS = "/status";
    public static final String CANCEL = "/cancel";

    // Product Catalog specific endpoints
    public static final String SLUG = "/slug";
    public static final String SLUG_PARAM = "/slug/{slug}";
    public static final String SKU = "/sku";
    public static final String SKU_PARAM = "/sku/{sku}";
    public static final String TREE = "/tree";
    public static final String ROOT = "/root";
    public static final String CHILDREN = "/children";
    public static final String PATH = "/path";
    public static final String FEATURED = "/featured";
    public static final String ACTIVE = "/active";
    public static final String MOVE = "/move";
    public static final String REVIEWS = "/reviews";
    public static final String INVENTORY = "/inventory";
    public static final String LOW_STOCK = "/low-stock";
    public static final String OUT_OF_STOCK = "/out-of-stock";
    public static final String ALERTS = "/alerts";
    public static final String REORDER = "/reorder";
    public static final String ADD = "/add";
    public static final String REMOVE = "/remove";
    public static final String SET = "/set";
    public static final String ADJUST = "/adjust";
    public static final String RESERVE = "/reserve";
    public static final String RELEASE = "/release";
    public static final String FULFILL = "/fulfill";
    public static final String STATS = "/stats";
    public static final String CHECK = "/check";
    public static final String MODERATE = "/moderate";
    public static final String MODERATION = "/moderation";
    public static final String BULK = "/bulk";
    public static final String HELPFUL = "/helpful";
    public static final String NOT_HELPFUL = "/not-helpful";
    public static final String SUGGESTIONS = "/suggestions";
    public static final String SUMMARY = "/summary";
    public static final String RECENT = "/recent";
}

