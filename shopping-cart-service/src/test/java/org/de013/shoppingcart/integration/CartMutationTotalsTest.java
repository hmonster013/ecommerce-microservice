package org.de013.shoppingcart.integration;

import org.de013.common.dto.ProductDetailDto;
import org.de013.shoppingcart.dto.request.AddToCartDto;
import org.de013.shoppingcart.dto.request.GiftOptionsDto;
import org.de013.shoppingcart.dto.request.UpdateCartItemDto;
import org.de013.shoppingcart.dto.response.CartResponseDto;
import org.de013.shoppingcart.entity.Cart;
import org.de013.shoppingcart.entity.CartItem;
import org.de013.shoppingcart.entity.RedisCart;
import org.de013.shoppingcart.entity.enums.CartType;
import org.de013.shoppingcart.repository.jpa.CartItemRepository;
import org.de013.shoppingcart.repository.jpa.CartRepository;
import org.de013.shoppingcart.repository.redis.RedisCartOperations;
import org.de013.shoppingcart.repository.redis.RedisCartRepository;
import org.de013.shoppingcart.repository.redis.RedisCartSessionManager;
import org.de013.shoppingcart.service.CartItemService;
import org.de013.shoppingcart.service.CartMergeService;
import org.de013.shoppingcart.service.CartService;
import org.de013.shoppingcart.service.CartTotalsService;
import org.de013.shoppingcart.service.ProductCatalogClient;
import org.de013.shoppingcart.service.impl.CartItemServiceImpl;
import org.de013.shoppingcart.service.impl.CartServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.cloud.config.enabled=false", "spring.config.import="})
@ActiveProfiles("test")
@ContextConfiguration(classes = CartMutationTotalsTest.JpaConfig.class)
@Import({CartTotalsService.class, CartItemServiceImpl.class, CartServiceImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CartMutationTotalsTest {
    @Configuration
    @EntityScan(basePackageClasses = Cart.class)
    @EnableJpaRepositories(basePackageClasses = CartRepository.class)
    static class JpaConfig { }
    @Autowired CartRepository carts;
    @Autowired CartItemRepository items;
    @Autowired CartItemService itemService;
    @Autowired CartService cartService;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean RedisCartRepository redis;
    @MockitoBean RedisCartOperations redisOperations;
    @MockitoBean RedisCartSessionManager sessions;
    @MockitoBean CartMergeService mergeService;
    @MockitoBean ProductCatalogClient catalog;

    private TransactionTemplate transactions;
    private final Map<Long, RedisCart> cache = new HashMap<>();

    @BeforeEach
    void setUp() {
        transactions = new TransactionTemplate(transactionManager);
        cache.clear();
        when(redis.save(any(RedisCart.class))).thenAnswer(call -> {
            RedisCart snapshot = call.getArgument(0);
            cache.put(snapshot.getCartId(), snapshot);
            return snapshot;
        });
        when(redis.findByCartId(anyLong())).thenAnswer(call -> Optional.ofNullable(cache.get(call.getArgument(0))));
    }

    @Test
    void updateAndDeletePersistTotalsAndReplaceCachedItemsWithoutDoubleDiscount() {
        Fixture fixture = fixture();
        itemService.updateCartItem(fixture.firstId(), UpdateCartItemDto.builder().quantity(4).build());
        assertTotals(fixture.cartId(), "37", "41", 2, 5);
        assertEquals(4, cache.get(fixture.cartId()).getItems().get(0).getQuantity());
        assertTrue(itemService.removeCartItem(fixture.firstId()));
        assertTotals(fixture.cartId(), "5", "9", 1, 1);
        assertEquals(fixture.secondId(), cache.get(fixture.cartId()).getItems().get(0).getItemId());
        assertTrue(itemService.removeCartItem(fixture.secondId()));
        assertTotals(fixture.cartId(), "0", "0", 0, 0);
        assertTrue(cache.get(fixture.cartId()).getItems().isEmpty());
        assertEquals(0, cache.get(fixture.cartId()).getTaxAmount().signum());
        assertEquals(0, cache.get(fixture.cartId()).getShippingAmount().signum());
        assertEquals(0, cache.get(fixture.cartId()).getDiscountAmount().signum());
    }

    @Test
    void clearReturnsAndPersistsEmptyCartOnBothReadPaths() {
        Fixture fixture = fixture();
        CartResponseDto cleared = cartService.clearCart(fixture.userId(), null);
        assertTrue(cleared.items().isEmpty());
        assertEquals(0, cleared.totalAmount().signum());
        assertEquals(0, cleared.taxAmount().signum());
        assertEquals(0, cleared.shippingAmount().signum());
        assertEquals(0, cleared.discountAmount().signum());
        assertTotals(fixture.cartId(), "0", "0", 0, 0);
        assertTrue(items.findByCartId(fixture.cartId()).isEmpty());
        assertTrue(cartService.getActiveCart(fixture.userId(), null).orElseThrow().items().isEmpty());
        assertTrue(cartService.getCartById(fixture.cartId()).orElseThrow().items().isEmpty());
    }

    @Test
    void repeatedAddUsesNetPriceAndUpdatesCountsInOneTransaction() {
        Fixture fixture = fixture();
        ProductDetailDto product = ProductDetailDto.builder().name("New product").sku("PROD1234")
                .price(new BigDecimal("8")).comparePrice(new BigDecimal("10")).build();
        when(catalog.getProductInfo("new-product")).thenReturn(product);
        AddToCartDto add = AddToCartDto.builder().productId("new-product").quantity(2).build();
        itemService.addItemToCart(fixture.cartId(), add);
        assertTotals(fixture.cartId(), "37", "41", 3, 5);
        itemService.addItemToCart(fixture.cartId(), add);
        assertTotals(fixture.cartId(), "53", "57", 3, 7);
    }

    @Test
    void giftOptionsRecalculateLineTotalsBeforePublishingTheCartSnapshot() {
        Fixture fixture = fixture();
        itemService.updateGiftOptions(fixture.firstId(), GiftOptionsDto.builder()
                .isGift(true).giftMessage("Happy birthday").build());
        assertTotals(fixture.cartId(), "22.50", "26.50", 2, 3);
        assertTrue(cache.get(fixture.cartId()).getItems().get(0).getIsGift());
    }

    @Test
    void serverPriceRefreshUpdatesCartTotalsWithoutAQuantityChange() {
        Fixture fixture = fixture();
        when(catalog.getProductInfo("first")).thenReturn(ProductDetailDto.builder()
                .price(new BigDecimal("9")).comparePrice(new BigDecimal("12")).build());
        itemService.updateCartItem(fixture.firstId(), UpdateCartItemDto.builder().refreshPrice(true).build());
        assertTotals(fixture.cartId(), "23", "27", 2, 3);
        assertEquals(0, new BigDecimal("3").compareTo(cache.get(fixture.cartId()).getItems().get(0).getDiscountAmount()));
    }

    @Test
    void rollbackKeepsDatabaseAndRedisAtThePreviousSnapshot() {
        Fixture fixture = fixture();
        transactions.executeWithoutResult(status -> {
            itemService.updateCartItem(fixture.firstId(), UpdateCartItemDto.builder().quantity(4).build());
            assertEquals(2, cache.get(fixture.cartId()).getItems().get(0).getQuantity());
            status.setRollbackOnly();
        });
        assertTotals(fixture.cartId(), "21", "25", 2, 3);
        verify(redis, never()).save(any());
    }

    @Test
    void rollbackRestoresDeletedItemsAndLeavesTheCachedCartUntouched() {
        Fixture fixture = fixture();
        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
            itemService.removeCartItem(fixture.firstId());
            throw new IllegalStateException("Abort the caller transaction");
        }));
        assertTrue(items.existsById(fixture.firstId()));
        assertTotals(fixture.cartId(), "21", "25", 2, 3);
        verify(redis, never()).save(any());
    }

    private Fixture fixture() {
        return transactions.execute(status -> {
            Cart cart = Cart.builder().userId(UUID.randomUUID().toString()).cartType(CartType.USER)
                    .expiresAt(LocalDateTime.now().plusDays(1))
                    .taxAmount(new BigDecimal("2")).shippingAmount(new BigDecimal("5"))
                    .discountAmount(new BigDecimal("3")).build();
            CartItem first = CartItem.builder().productId("first").productName("Discounted product")
                    .quantity(2).unitPrice(new BigDecimal("10")).discountAmount(new BigDecimal("2"))
                    .giftWrapPrice(new BigDecimal("1.50")).build();
            first.calculateTotalPrice();
            CartItem second = CartItem.builder().productId("second").productName("Second product")
                    .quantity(1).unitPrice(new BigDecimal("5")).build();
            second.calculateTotalPrice();
            cart.addItem(first);
            cart.addItem(second);
            carts.saveAndFlush(cart);
            cache.put(cart.getId(), RedisCart.fromCart(cart));
            return new Fixture(cart.getId(), first.getId(), second.getId(), cart.getUserId());
        });
    }

    private void assertTotals(Long id, String subtotal, String total, int count, int quantity) {
        Cart persisted = carts.findById(id).orElseThrow();
        assertEquals(0, new BigDecimal(subtotal).compareTo(persisted.getSubtotal()));
        assertEquals(0, new BigDecimal(total).compareTo(persisted.getTotalAmount()));
        assertEquals(count, persisted.getItemCount());
        assertEquals(quantity, persisted.getTotalQuantity());
        RedisCart snapshot = cache.get(id);
        assertEquals(0, persisted.getSubtotal().compareTo(snapshot.getSubtotal()));
        assertEquals(0, persisted.getTotalAmount().compareTo(snapshot.getTotalAmount()));
        assertEquals(count, snapshot.getItems().size());
        assertEquals(quantity, snapshot.getTotalQuantity());
    }

    private record Fixture(Long cartId, Long firstId, Long secondId, String userId) { }
}
