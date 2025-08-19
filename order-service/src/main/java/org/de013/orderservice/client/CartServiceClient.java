package org.de013.orderservice.client;

import org.de013.orderservice.dto.integration.cart.CartDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "shopping-cart-service", path = "/api/v1/carts")
public interface CartServiceClient {

    @GetMapping
    CartDto getOrCreateCart(@RequestParam(required = false) String userId,
                            @RequestParam(required = false) String sessionId);

    @DeleteMapping("/clear")
    void clearCart(@RequestParam(required = false) String userId,
                   @RequestParam(required = false) String sessionId);
}

