package org.de013.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.de013.orderservice.dto.request.CreateOrderRequest;
import org.de013.orderservice.dto.request.UpdateOrderRequest;
import org.de013.orderservice.exception.BadRequestException;
import org.de013.orderservice.service.OrderValidationService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
public class OrderValidationServiceImpl implements OrderValidationService {

    @Override
    public void validateCreate(CreateOrderRequest request) {
        if (request == null) throw new BadRequestException("Request is null");
        if (request.getUserId() == null) throw new BadRequestException("userId is required");
        if (request.getOrderType() == null) throw new BadRequestException("orderType is required");
        if (request.getShippingAddress() == null) throw new BadRequestException("shippingAddress is required");
        if (request.getCurrency() == null) throw new BadRequestException("currency is required");
        // Items will be sourced from cart via CartIntegrationService during processing
    }

    @Override
    public void validateUpdate(UpdateOrderRequest request) {
        if (request == null) throw new BadRequestException("Request is null");
        // TODO: validate allowed fields to update
    }
}

