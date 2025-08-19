package org.de013.orderservice.service.integration.impl;

import lombok.RequiredArgsConstructor;
import org.de013.orderservice.dto.integration.cart.CartDto;
import org.de013.orderservice.dto.request.CreateOrderRequest;
import org.de013.orderservice.entity.enums.OrderType;
import org.de013.orderservice.entity.valueobject.Address;
import org.de013.orderservice.service.integration.CartIntegrationService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CartIntegrationServiceImpl implements CartIntegrationService {

    private final org.de013.orderservice.client.CartServiceClient cartClient;

    @Override
    public CreateOrderRequest buildCreateOrderRequestFromCart(Long userId) {
        CartDto cart = cartClient.getOrCreateCart(String.valueOf(userId), null);
        if (cart == null || cart.getCartId() == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cart is empty or not found for user " + userId);
        }
        // Basic mapping; addresses and payment method to be provided by caller or separate profile record
        return CreateOrderRequest.builder()
                .userId(userId)
                .cartId(cart.getCartId())
                .orderType(OrderType.STANDARD)
                .shippingAddress(Address.builder().country("US").city("Unknown").line1("TBD").postalCode("00000").build())
                .billingAddress(null)
                .paymentMethod(CreateOrderRequest.PaymentMethodDto.builder().type("CASH_ON_DELIVERY").authorizeOnly(true).build())
                .shippingMethod("STANDARD")
                .currency(cart.getCurrency() != null ? cart.getCurrency() : "USD")
                .orderSource("WEB")
                .customerNotes(null)
                .build();
    }

    @Override
    public void clearCart(Long userId) {
        cartClient.clearCart(String.valueOf(userId), null);
    }
}

