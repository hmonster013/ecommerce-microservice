package org.de013.shoppingcart.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.entity.Cart;
import org.de013.shoppingcart.entity.CartItem;
import org.de013.shoppingcart.entity.RedisCart;
import org.de013.shoppingcart.repository.jpa.CartItemRepository;
import org.de013.shoppingcart.repository.jpa.CartRepository;
import org.de013.shoppingcart.repository.redis.RedisCartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartTotalsService {
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final RedisCartRepository redisCartRepository;

    @Transactional
    public Cart recalculate(Long cartId) {
        // Flush lifecycle callbacks and deletes before reading the surviving line totals.
        cartItemRepository.flush();
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found"));
        List<CartItem> items = cartItemRepository.findByCartId(cartId);
        cart.setItemCount(items.size());
        cart.setTotalQuantity(items.stream().mapToInt(CartItem::getQuantity).sum());
        cart.setSubtotal(items.stream().map(CartItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        cart.setTaxAmount(items.isEmpty() ? BigDecimal.ZERO : amount(cart.getTaxAmount()));
        cart.setShippingAmount(items.isEmpty() ? BigDecimal.ZERO : amount(cart.getShippingAmount()));
        cart.setDiscountAmount(items.isEmpty() ? BigDecimal.ZERO : amount(cart.getDiscountAmount()));
        // Line totals already include per-unit discounts; only cart-level adjustments remain.
        cart.setTotalAmount(cart.getSubtotal().add(cart.getTaxAmount())
                .add(cart.getShippingAmount()).subtract(cart.getDiscountAmount()));
        cart.setLastActivityAt(LocalDateTime.now());
        cartRepository.saveAndFlush(cart);

        RedisCart snapshot = RedisCart.fromCart(cart);
        snapshot.setItems(items.stream().map(RedisCart.RedisCartItem::fromCartItem).toList());
        // A rolled-back item/cart mutation must never become visible through Redis.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    redisCartRepository.save(snapshot);
                } catch (RuntimeException error) {
                    log.error("Cart {} committed but Redis snapshot could not be updated", cartId, error);
                }
            }
        });
        return cart;
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
