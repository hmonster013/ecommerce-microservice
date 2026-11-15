package org.de013.orderservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.de013.common.exception.ConflictException;
import org.de013.orderservice.client.CartClient;
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
    private final CheckoutAttemptRepository attempts;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final CartClient cartClient;
    private final ObjectMapper objectMapper;
    private final AllocationRecovery recovery;

    public CheckoutOrderResponse create(UUID key, String userId, CreateOrderRequest request) {
        if (!"USD".equals(request.currency()) || !"CARD".equals(request.paymentMethod()))
            throw new IllegalArgumentException("Hosted checkout requires CARD and USD");
        return create(key.toString(), userId, request);
    }

    public CheckoutOrderResponse create(String key, String userId, CreateOrderRequest request) {
        if (key == null || key.isBlank() || key.length() > 36 || !key.equals(key.trim()))
            throw new IllegalArgumentException("Invalid idempotency key");
        request = request.withUserId(userId);
        String snapshot;
        try { snapshot = objectMapper.writeValueAsString(request); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid request", e); }
        var existing = attempts.findById(key);
        if (existing.isPresent()) return replay(key, userId, snapshot);
        var cart = cartClient.getCart(request.cartId()).getData();
        if (cart == null || !request.cartId().equals(cart.cartId()) || !userId.equals(cart.userId()))
            throw new AccessDeniedException("Cart owner mismatch");
        if (cart.items() == null || cart.items().isEmpty() || request.currency() == null || !request.currency().equals(cart.currency()))
            throw new IllegalArgumentException("Invalid cart or currency");
        for (var item : cart.items()) {
            try {
                if (item.productId() == null || Long.parseLong(item.productId()) <= 0 || (item.variantId() != null && Long.parseLong(item.variantId()) <= 0)
                        || item.quantity() == null || item.quantity() <= 0 || item.unitPrice() == null
                        || item.unitPrice().signum() < 0 || !request.currency().equals(item.currency()))
                    throw new IllegalArgumentException("Invalid cart item");
            } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid product ID", e); }
        }
        try { var created = orderService.createIntent(request, key, snapshot, cart.items()); recovery.process(created.id()); }
        catch (org.springframework.dao.DuplicateKeyException e) { return replay(key, userId, snapshot); }
        return read(key, userId);
    }

    private CheckoutOrderResponse replay(String key, String userId, String snapshot) {
        var attempt = attempts.findById(key).orElseThrow(() -> new ConflictException("Attempt conflict"));
        if (!attempt.getUserId().equals(userId)) throw new AccessDeniedException("Checkout owner mismatch");
        if (attempt.getRequestSnapshot() == null || !snapshot.equals(attempt.getRequestSnapshot())) throw new ConflictException("Idempotency payload mismatch");
        return read(key, userId);
    }

    public CheckoutOrderResponse read(UUID key, String userId) { return read(key.toString(), userId); }
    public CheckoutOrderResponse read(String key, String userId) {
        var attempt = attempts.findById(key);
        if (attempt.isEmpty()) return new CheckoutOrderResponse("NOT_STARTED", null);
        if (!attempt.get().getUserId().equals(userId)) throw new AccessDeniedException("Checkout owner mismatch");
        return orders.findByCheckoutKeyAndUserId(key, userId).map(order -> {
            if ("FAILED".equals(order.getAllocationPhase())) return new CheckoutOrderResponse("FAILED", null);
            if (order.getAllocationPhase() == null || java.util.Set.of("READY", "FULFILL_PENDING", "FULFILLED").contains(order.getAllocationPhase())) return new CheckoutOrderResponse("READY", orderService.getOrderById(order.getId()));
            return new CheckoutOrderResponse("UNKNOWN", null);
        }).orElseGet(() -> new CheckoutOrderResponse("UNKNOWN", null));
    }
}
