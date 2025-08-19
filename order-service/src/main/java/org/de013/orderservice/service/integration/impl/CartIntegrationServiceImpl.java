package org.de013.orderservice.service.integration.impl;

import lombok.RequiredArgsConstructor;
import org.de013.orderservice.dto.request.CreateOrderRequest;
import org.de013.orderservice.service.integration.CartIntegrationService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CartIntegrationServiceImpl implements CartIntegrationService {

    @Override
    public CreateOrderRequest buildCreateOrderRequestFromCart(Long userId) {
        // Placeholder: in real impl, call cart service and map to CreateOrderRequest
        return CreateOrderRequest.builder()
                .userId(userId)
                .orderSource("WEB")
                .build();
    }

    @Override
    public void clearCart(Long userId) {
        // Placeholder: call cart service to clear cart
    }
}

