package org.de013.orderservice.service.integration.impl;

import lombok.RequiredArgsConstructor;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.service.integration.ShippingIntegrationService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ShippingIntegrationServiceImpl implements ShippingIntegrationService {

    @Override
    public void calculateShipping(Order order) {
        // Placeholder: calculate shipping cost via shipping service
    }

    @Override
    public void createShipment(Order order) {
        // Placeholder: create shipment and tracking via shipping service
    }
}

