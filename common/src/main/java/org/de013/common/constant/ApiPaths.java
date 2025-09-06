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
    public static final String PAYMENT_METHODS = "/payment-methods";
    public static final String REFUNDS = "/refunds";
    public static final String WEBHOOKS = "/webhooks";
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
    public static final String PAYMENT_ID_PARAM = "/{paymentId}";
    public static final String PAYMENT_METHOD_ID_PARAM = "/{paymentMethodId}";
    public static final String REFUND_ID_PARAM = "/{refundId}";
    public static final String PAYMENT_NUMBER_PARAM = "/{paymentNumber}";
    public static final String REFUND_NUMBER_PARAM = "/{refundNumber}";
    public static final String EVENT_TYPE_PARAM = "/{eventType}";
    public static final String STATUS_PARAM = "/{status}";
    public static final String TYPE_PARAM = "/{type}";
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
    public static final String ORDER = "/order";
    public static final String TYPE = "/type";

    // Payment specific endpoints
    public static final String PROCESS = "/process";
    public static final String CONFIRM = "/confirm";
    public static final String CAPTURE = "/capture";
    public static final String SYNC = "/sync";
    public static final String STATISTICS = "/statistics";
    public static final String SUCCESSFUL = "/successful";
    public static final String FAILED = "/failed";
    public static final String PENDING = "/pending";
    public static final String TOTAL_AMOUNT = "/total-amount";
    public static final String COUNT = "/count";

    // Payment Method specific endpoints
    public static final String DEFAULT = "/default";
    public static final String SET_DEFAULT = "/set-default";
    public static final String ACTIVATE = "/activate";
    public static final String DEACTIVATE = "/deactivate";
    public static final String ATTACH_CUSTOMER = "/attach-customer";
    public static final String DETACH_CUSTOMER = "/detach-customer";
    public static final String CARDS = "/cards";
    public static final String EXPIRED = "/expired";
    public static final String EXPIRING_SOON = "/expiring-soon";
    public static final String CLEANUP = "/cleanup";
    public static final String INACTIVE = "/inactive";
    public static final String ORPHANED = "/orphaned";

    // Refund specific endpoints
    public static final String APPROVE = "/approve";
    public static final String REJECT = "/reject";
    public static final String REQUIRING_APPROVAL = "/requiring-approval";
    public static final String CAN_REFUND = "/can-refund";
    public static final String VALID_AMOUNT = "/valid-amount";

    // Webhook specific endpoints
    public static final String STRIPE = "/stripe";
    public static final String VERIFY = "/verify";
    public static final String PARSE = "/parse";
    public static final String EVENT_TYPES = "/event-types";
    public static final String VALID = "/valid";
    public static final String ERROR = "/error";
    public static final String TEST = "/test";

    // Stripe specific endpoints
    public static final String PAYMENT_INTENTS = "/payment-intents";
    public static final String CUSTOMERS = "/customers";
    public static final String CUSTOMER_ID_PARAM = "/{customerId}";

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

    // Additional payment service endpoints
    public static final String PAYMENT = "/payment";
    public static final String ACTIVE = "/active";
}

