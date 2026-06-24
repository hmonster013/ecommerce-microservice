package org.de013.orderservice.client;

import org.de013.common.dto.ApiResponse;
import org.de013.orderservice.config.FeignConfig;
import org.de013.orderservice.dto.CartItemDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "shopping-cart-service", path = "/cart-items", configuration = FeignConfig.class)
public interface CartServiceClient {

    @GetMapping("/cart/{cartId}")
    ApiResponse<List<CartItemDto>> getCartItems(@PathVariable("cartId") Long cartId);
}
