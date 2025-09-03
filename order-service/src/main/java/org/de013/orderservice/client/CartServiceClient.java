package org.de013.orderservice.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Simple REST client for Shopping Cart Service
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CartServiceClient {

    private final RestTemplate restTemplate;
    
    @Value("${app.services.shopping-cart.url:http://localhost:8080/api/v1/cartsv}")
    private String cartServiceUrl;

    /**
     * Get cart items by cart ID
     */
    public List<CartItemDto> getCartItems(Long cartId) {
        try {
            log.debug("Getting cart items for cart: {}", cartId);
            String url = cartServiceUrl + "/cart-items/cart/" + cartId;

            CartItemDto[] items = restTemplate.getForObject(url, CartItemDto[].class);
            return items != null ? List.of(items) : List.of();

        } catch (Exception e) {
            log.error("Error getting cart items for cart {}: {}", cartId, e.getMessage());
            throw new RuntimeException("Failed to get cart items from cart service", e);
        }
    }

    /**
     * Simple DTO for cart item data - matches CartItemResponseDto from Shopping Cart Service
     */
    public static class CartItemDto {
        @JsonProperty("item_id")
        private Long itemId;

        @JsonProperty("product_id")
        private String productId;

        @JsonProperty("product_sku")
        private String productSku;

        @JsonProperty("product_name")
        private String productName;

        @JsonProperty("product_description")
        private String productDescription;

        @JsonProperty("product_image_url")
        private String productImageUrl;

        private Integer quantity;

        @JsonProperty("unit_price")
        private BigDecimal unitPrice;

        @JsonProperty("total_price")
        private BigDecimal totalPrice;

        private String currency;

        @JsonProperty("special_instructions")
        private String specialInstructions;

        @JsonProperty("is_gift")
        private Boolean isGift;

        @JsonProperty("gift_message")
        private String giftMessage;

        @JsonProperty("added_at")
        private LocalDateTime addedAt;

        // Getters and setters
        public Long getItemId() { return itemId; }
        public void setItemId(Long itemId) { this.itemId = itemId; }
        
        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        
        public String getProductSku() { return productSku; }
        public void setProductSku(String productSku) { this.productSku = productSku; }
        
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        
        public String getProductDescription() { return productDescription; }
        public void setProductDescription(String productDescription) { this.productDescription = productDescription; }
        
        public String getProductImageUrl() { return productImageUrl; }
        public void setProductImageUrl(String productImageUrl) { this.productImageUrl = productImageUrl; }
        
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        
        public BigDecimal getTotalPrice() { return totalPrice; }
        public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }
        
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        
        public String getSpecialInstructions() { return specialInstructions; }
        public void setSpecialInstructions(String specialInstructions) { this.specialInstructions = specialInstructions; }
        
        public Boolean getIsGift() { return isGift; }
        public void setIsGift(Boolean isGift) { this.isGift = isGift; }
        
        public String getGiftMessage() { return giftMessage; }
        public void setGiftMessage(String giftMessage) { this.giftMessage = giftMessage; }
        
        public LocalDateTime getAddedAt() { return addedAt; }
        public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }
    }
}
