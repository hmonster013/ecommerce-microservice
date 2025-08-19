package org.de013.orderservice.dto.integration.cart;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CartDto {
    @JsonProperty("cart_id")
    private Long cartId;
    @JsonProperty("user_id")
    private String userId;
    private String currency;
    private List<CartItemDto> items;

    @Data
    public static class CartItemDto {
        @JsonProperty("item_id")
        private Long itemId;
        @JsonProperty("product_id")
        private String productId;
        private Integer quantity;
        @JsonProperty("unit_price")
        private BigDecimal unitPrice;
        @JsonProperty("total_price")
        private BigDecimal totalPrice;
    }
}

