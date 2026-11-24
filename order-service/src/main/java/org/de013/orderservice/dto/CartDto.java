package org.de013.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CartDto(@JsonProperty("cart_id") Long cartId,
                      @JsonProperty("user_id") String userId,
                      String currency, List<CartItemDto> items) { }
