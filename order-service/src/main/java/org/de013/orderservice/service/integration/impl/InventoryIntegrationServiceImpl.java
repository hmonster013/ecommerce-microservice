package org.de013.orderservice.service.integration.impl;

import lombok.RequiredArgsConstructor;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.service.integration.InventoryIntegrationService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryIntegrationServiceImpl implements InventoryIntegrationService {

    @Override
    public void reserve(Order order) {
        // Placeholder: reserve stock via inventory service
    }

    @Override
    public void release(Order order) {
        // Placeholder: release stock via inventory service
    }
}

