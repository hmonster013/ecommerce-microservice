package org.de013.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CartItemDto(
        @JsonProperty("item_id")
        Long itemId,

        @JsonProperty("product_id")
        String productId,

        @JsonProperty("product_sku")
        String productSku,

        @JsonProperty("product_name")
        String productName,

        @JsonProperty("product_description")
        String productDescription,

        @JsonProperty("product_image_url")
        String productImageUrl,

        Integer quantity,

        @JsonProperty("unit_price")
        BigDecimal unitPrice,

        @JsonProperty("total_price")
        BigDecimal totalPrice,

        String currency,

        @JsonProperty("special_instructions")
        String specialInstructions,

        @JsonProperty("is_gift")
        Boolean isGift,

        @JsonProperty("gift_message")
        String giftMessage,

        @JsonProperty("added_at")
        LocalDateTime addedAt,

        @JsonProperty("category_name")
        String categoryName,

        @JsonProperty("product_brand")
        String productBrand,

        @JsonProperty("discount_amount")
        BigDecimal discountAmount
) {
}
