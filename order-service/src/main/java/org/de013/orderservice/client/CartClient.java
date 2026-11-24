package org.de013.orderservice.client;

import org.de013.common.dto.ApiResult;
import org.de013.orderservice.config.FeignConfig;
import org.de013.orderservice.dto.CartDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "shopping-cart-service", contextId = "orderCart", path = "/carts", configuration = FeignConfig.class, fallbackFactory = org.de013.orderservice.config.FeignResilienceConfig.RethrowFallbackFactory.class)
public interface CartClient {
    @GetMapping("/{id}")
    ApiResult<CartDto> getCart(@PathVariable("id") Long id);
}
