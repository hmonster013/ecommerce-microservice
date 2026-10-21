package org.de013.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.de013.orderservice.dto.request.CreateOrderRequest;
import org.de013.orderservice.dto.response.CheckoutOrderResponse;
import org.de013.orderservice.repository.CheckoutAttemptRepository;
import org.de013.orderservice.repository.OrderRepository;
import org.de013.orderservice.service.OrderService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckoutOrderService {
    private final CheckoutAttemptStore store;
    private final CheckoutAttemptRepository attempts;
    private final OrderRepository orders;
    private final OrderService orderService;

    public CheckoutOrderResponse create(UUID key, String userId, CreateOrderRequest request) {
        if (!"USD".equals(request.currency()) || !"CARD".equals(request.paymentMethod())) {
            throw new IllegalArgumentException("Hosted checkout requires CARD and USD");
        }
        try {
            store.reserve(key.toString(), userId);
        } catch (DataIntegrityViolationException duplicate) {
            return read(key, userId);
        }
        // Reservation commits before remote stock mutations. An uncertain failure must not replay them.
        return new CheckoutOrderResponse("READY", orderService.createCheckoutOrder(request.withUserId(userId), key.toString()));
    }

    public CheckoutOrderResponse read(UUID key, String userId) {
        var attempt = attempts.findById(key.toString());
        if (attempt.isEmpty()) return new CheckoutOrderResponse("NOT_STARTED", null);
        if (!attempt.get().getUserId().equals(userId)) throw new AccessDeniedException("Checkout owner mismatch");
        return orders.findByCheckoutKeyAndUserId(key.toString(), userId)
                // Map lazy order items inside OrderService's read transaction (OSIV is disabled).
                .map(order -> new CheckoutOrderResponse("READY", orderService.getOrderById(order.getId())))
                .orElseGet(() -> new CheckoutOrderResponse("UNKNOWN", null));
    }
}
