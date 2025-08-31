package org.de013.shoppingcart.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.de013.shoppingcart.entity.enums.CartStatus;
import org.de013.shoppingcart.entity.enums.CartType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for cart response
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
@Schema(description = "Shopping cart response")
public class CartResponseDto {

    @Schema(description = "Cart ID", example = "123")
    @JsonProperty("cart_id")
    private Long cartId;

    @Schema(description = "User ID (for authenticated users)", example = "user-123e4567-e89b-12d3-a456-426614174000")
    @JsonProperty("user_id")
    private String userId;

    @Schema(description = "Session ID (for guest users)", example = "sess-123e4567-e89b-12d3-a456-426614174000")
    @JsonProperty("session_id")
    private String sessionId;

    @Schema(description = "Cart status", example = "ACTIVE")
    private CartStatus status;

    @Schema(description = "Cart type", example = "USER")
    @JsonProperty("cart_type")
    private CartType cartType;

    @Schema(description = "Cart expiration timestamp", example = "2024-12-31T23:59:59")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;

    @Schema(description = "Cart creation timestamp", example = "2024-01-01T10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "Cart last update timestamp", example = "2024-01-01T15:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @Schema(description = "Last activity timestamp", example = "2024-01-01T15:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("last_activity_at")
    private LocalDateTime lastActivityAt;

    @Schema(description = "Currency code", example = "USD")
    private String currency;

    @Schema(description = "Cart subtotal", example = "99.98")
    private BigDecimal subtotal;

    @Schema(description = "Tax amount", example = "8.00")
    @JsonProperty("tax_amount")
    private BigDecimal taxAmount;

    @Schema(description = "Shipping amount", example = "9.99")
    @JsonProperty("shipping_amount")
    private BigDecimal shippingAmount;

    @Schema(description = "Discount amount", example = "10.00")
    @JsonProperty("discount_amount")
    private BigDecimal discountAmount;

    @Schema(description = "Total amount", example = "107.97")
    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    @Schema(description = "Number of unique items", example = "3")
    @JsonProperty("item_count")
    private Integer itemCount;

    @Schema(description = "Total quantity of all items", example = "5")
    @JsonProperty("total_quantity")
    private Integer totalQuantity;

    @Schema(description = "Applied coupon code", example = "SAVE20")
    @JsonProperty("coupon_code")
    private String couponCode;

    @Schema(description = "Cart notes", example = "Birthday gift order")
    private String notes;

    @Schema(description = "List of items in the cart")
    private List<CartItemResponseDto> items;

    @Schema(description = "Whether cart is empty", example = "false")
    @JsonProperty("is_empty")
    private Boolean isEmpty;

    @Schema(description = "Whether cart is expired", example = "false")
    @JsonProperty("is_expired")
    private Boolean isExpired;

    @Schema(description = "Whether cart can be modified", example = "true")
    @JsonProperty("can_be_modified")
    private Boolean canBeModified;

    @Schema(description = "Whether cart is ready for checkout", example = "true")
    @JsonProperty("is_checkout_ready")
    private Boolean isCheckoutReady;

    @Schema(description = "Time until cart expires (in seconds)", example = "3600")
    @JsonProperty("expires_in_seconds")
    private Long expiresInSeconds;

    @Schema(description = "Checkout URL for this cart", example = "/checkout/123")
    @JsonProperty("checkout_url")
    private String checkoutUrl;

    @Schema(description = "Share URL for this cart", example = "/cart/share/abc123")
    @JsonProperty("share_url")
    private String shareUrl;

    @Schema(description = "Cart validation status")
    @JsonProperty("validation_status")
    private Map<String, Object> validationStatus;




    /**
     * Calculate time until expiration in seconds
     */
    public Long calculateExpiresInSeconds() {
        if (expiresAt == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        if (expiresAt.isBefore(now)) {
            return 0L;
        }
        return java.time.Duration.between(now, expiresAt).getSeconds();
    }

    /**
     * Check if cart has any items
     */
    public boolean hasItems() {
        return items != null && !items.isEmpty();
    }

    /**
     * Get total savings amount
     */
    public BigDecimal getTotalSavings() {
        if (discountAmount == null) {
            return BigDecimal.ZERO;
        }
        return discountAmount;
    }


}
