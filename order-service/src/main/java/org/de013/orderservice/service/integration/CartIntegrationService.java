package org.de013.orderservice.service.integration;

import org.de013.orderservice.dto.request.CreateOrderRequest;

public interface CartIntegrationService {
    CreateOrderRequest buildCreateOrderRequestFromCart(Long userId);
    void clearCart(Long userId);
}

