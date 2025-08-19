package org.de013.orderservice.service;

import org.de013.orderservice.dto.request.CreateOrderRequest;
import org.de013.orderservice.dto.request.UpdateOrderRequest;

public interface OrderValidationService {
    void validateCreate(CreateOrderRequest request);
    void validateUpdate(UpdateOrderRequest request);
}

