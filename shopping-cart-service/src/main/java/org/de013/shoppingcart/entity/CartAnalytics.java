package org.de013.shoppingcart.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CartAnalytics Entity
 * Stores analytics and metrics data for shopping carts
 */
@Entity
@Table(name = "cart_analytics", indexes = {
    @Index(name = "idx_cart_analytics_cart_id", columnList = "cart_id"),
    @Index(name = "idx_cart_analytics_user_id", columnList = "user_id"),
    @Index(name = "idx_cart_analytics_session_id", columnList = "session_id"),
    @Index(name = "idx_cart_analytics_created_at", columnList = "created_at"),
    @Index(name = "idx_cart_analytics_event_type", columnList = "event_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(callSuper = true)
public class CartAnalytics extends BaseEntity {

    @Column(name = "cart_id", nullable = false)
    private Long cartId;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType; // CREATED, ITEM_ADDED, ITEM_REMOVED, UPDATED, ABANDONED, CONVERTED, etc.

    @Column(name = "event_timestamp", nullable = false)
    private LocalDateTime eventTimestamp;

    @Column(name = "product_id", length = 36)
    private String productId;

    @Column(name = "product_sku", length = 100)
    private String productSku;

    @Column(name = "category_id", length = 36)
    private String categoryId;

    @Column(name = "quantity_before")
    private Integer quantityBefore;

    @Column(name = "quantity_after")
    private Integer quantityAfter;

    @Column(name = "price_before", precision = 19, scale = 2)
    private BigDecimal priceBefore;

    @Column(name = "price_after", precision = 19, scale = 2)
    private BigDecimal priceAfter;

    @Column(name = "cart_total_before", precision = 19, scale = 2)
    private BigDecimal cartTotalBefore;

    @Column(name = "cart_total_after", precision = 19, scale = 2)
    private BigDecimal cartTotalAfter;

    @Column(name = "item_count_before")
    private Integer itemCountBefore;

    @Column(name = "item_count_after")
    private Integer itemCountAfter;

    @Column(name = "source", length = 50)
    private String source; // WEB, MOBILE_APP, API, etc.

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "referrer_url", length = 1000)
    private String referrerUrl;

    @Column(name = "page_url", length = 1000)
    private String pageUrl;

    @Column(name = "device_type", length = 20)
    private String deviceType; // DESKTOP, MOBILE, TABLET

    @Column(name = "browser", length = 50)
    private String browser;

    @Column(name = "operating_system", length = 50)
    private String operatingSystem;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "discount_code", length = 50)
    private String discountCode;

    @Column(name = "campaign_id", length = 100)
    private String campaignId;

    @Column(name = "utm_source", length = 100)
    private String utmSource;

    @Column(name = "utm_medium", length = 100)
    private String utmMedium;

    @Column(name = "utm_campaign", length = 100)
    private String utmCampaign;

    @Column(name = "utm_term", length = 100)
    private String utmTerm;

    @Column(name = "utm_content", length = 100)
    private String utmContent;

    @Column(name = "time_spent_seconds")
    private Integer timeSpentSeconds;

    @Column(name = "page_views_count")
    private Integer pageViewsCount;

    @Column(name = "is_returning_user", nullable = false)
    @Builder.Default
    private Boolean isReturningUser = false;

    @Column(name = "previous_cart_count")
    private Integer previousCartCount;

    @Column(name = "days_since_last_cart")
    private Integer daysSinceLastCart;

    @Column(name = "additional_data", length = 2000)
    private String additionalData; // JSON format for flexible data storage

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    /**
     * Create analytics event for cart creation
     */
    public static CartAnalytics createCartCreatedEvent(Long cartId, String userId, String sessionId) {
        return CartAnalytics.builder()
                .cartId(cartId)
                .userId(userId)
                .sessionId(sessionId)
                .eventType("CART_CREATED")
                .eventTimestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create analytics event for item addition
     */
    public static CartAnalytics createItemAddedEvent(Long cartId, String userId, String sessionId, 
                                                   String productId, Integer quantity, BigDecimal price) {
        return CartAnalytics.builder()
                .cartId(cartId)
                .userId(userId)
                .sessionId(sessionId)
                .eventType("ITEM_ADDED")
                .eventTimestamp(LocalDateTime.now())
                .productId(productId)
                .quantityAfter(quantity)
                .priceAfter(price)
                .build();
    }

    /**
     * Create analytics event for item removal
     */
    public static CartAnalytics createItemRemovedEvent(Long cartId, String userId, String sessionId, 
                                                     String productId, Integer quantityBefore) {
        return CartAnalytics.builder()
                .cartId(cartId)
                .userId(userId)
                .sessionId(sessionId)
                .eventType("ITEM_REMOVED")
                .eventTimestamp(LocalDateTime.now())
                .productId(productId)
                .quantityBefore(quantityBefore)
                .quantityAfter(0)
                .build();
    }

    /**
     * Create analytics event for cart abandonment
     */
    public static CartAnalytics createCartAbandonedEvent(Long cartId, String userId, String sessionId, 
                                                       BigDecimal cartTotal, Integer itemCount) {
        return CartAnalytics.builder()
                .cartId(cartId)
                .userId(userId)
                .sessionId(sessionId)
                .eventType("CART_ABANDONED")
                .eventTimestamp(LocalDateTime.now())
                .cartTotalAfter(cartTotal)
                .itemCountAfter(itemCount)
                .build();
    }

    /**
     * Create analytics event for cart conversion
     */
    public static CartAnalytics createCartConvertedEvent(Long cartId, String userId, String sessionId, 
                                                       BigDecimal cartTotal, Integer itemCount, String orderId) {
        return CartAnalytics.builder()
                .cartId(cartId)
                .userId(userId)
                .sessionId(sessionId)
                .eventType("CART_CONVERTED")
                .eventTimestamp(LocalDateTime.now())
                .cartTotalAfter(cartTotal)
                .itemCountAfter(itemCount)
                .additionalData("{\"orderId\":\"" + orderId + "\"}")
                .build();
    }
}
