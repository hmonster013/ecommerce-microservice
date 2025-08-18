package org.de013.shoppingcart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Product information DTO for integration with Product Catalog Service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductInfo {
    private String sku;
    private String name;
    private String description;
    private String imageUrl;
    private String categoryId;
    private String categoryName;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stockQuantity;
    private String status;
}
