package org.de013.orderservice.service.impl;

import org.de013.common.dto.ApiResponse;
import org.de013.orderservice.client.CartServiceClient;
import org.de013.orderservice.client.ProductCatalogClient;
import org.de013.orderservice.dto.CartItemDto;
import org.de013.orderservice.dto.request.CreateOrderRequest;
import org.de013.orderservice.dto.response.OrderResponse;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.entity.enums.OrderType;
import org.de013.orderservice.mapper.OrderMapper;
import org.de013.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Spy
    private OrderMapper orderMapper = new OrderMapper();

    @Mock
    private CartServiceClient cartServiceClient;

    @Mock
    private ProductCatalogClient productCatalogClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    private CreateOrderRequest createOrderRequest;
    private List<CartItemDto> cartItems;

    @BeforeEach
    void setUp() {
        createOrderRequest = CreateOrderRequest.builder()
                .userId("user_123")
                .cartId(123L)
                .orderType(OrderType.STANDARD)
                .currency("USD")
                .paymentMethod("CREDIT_CARD")
                .build();

        CartItemDto item1 = new CartItemDto();
        item1.setProductId("prod_1");
        item1.setProductSku("SKU-1");
        item1.setProductName("Product 1");
        item1.setQuantity(2);
        item1.setUnitPrice(BigDecimal.TEN);
        item1.setCurrency("USD");

        CartItemDto item2 = new CartItemDto();
        item2.setProductId("prod_2");
        item2.setProductSku("SKU-2");
        item2.setProductName("Product 2");
        item2.setQuantity(1);
        item2.setUnitPrice(BigDecimal.valueOf(20));
        item2.setCurrency("USD");

        cartItems = Arrays.asList(item1, item2);
    }

    @Test
    void testCreateOrder_HappyPath_Success() {
        // Arrange
        ApiResponse<List<CartItemDto>> cartResponse = ApiResponse.success(cartItems);
        when(cartServiceClient.getCartItems(123L)).thenReturn(cartResponse);
        when(orderRepository.existsByOrderNumber(anyString())).thenReturn(false);

        ApiResponse<Boolean> successResponse = ApiResponse.success(true);
        when(productCatalogClient.reserveStock("prod_1", 2)).thenReturn(successResponse);
        when(productCatalogClient.reserveStock("prod_2", 1)).thenReturn(successResponse);

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order savedOrder = invocation.getArgument(0);
            savedOrder.setId(100L);
            return savedOrder;
        });

        // Act
        OrderResponse response = orderService.createOrder(createOrderRequest);

        // Assert
        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(OrderStatus.PENDING, response.getStatus());
        verify(productCatalogClient, times(1)).reserveStock("prod_1", 2);
        verify(productCatalogClient, times(1)).reserveStock("prod_2", 1);
        verify(productCatalogClient, never()).releaseStock(anyString(), anyInt());
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void testCreateOrder_ReservationFailsOnSecondItem_CompensatesFirstItemAndThrows() {
        // Arrange
        ApiResponse<List<CartItemDto>> cartResponse = ApiResponse.success(cartItems);
        when(cartServiceClient.getCartItems(123L)).thenReturn(cartResponse);
        when(orderRepository.existsByOrderNumber(anyString())).thenReturn(false);

        ApiResponse<Boolean> successResponse = ApiResponse.success(true);
        ApiResponse<Boolean> failResponse = ApiResponse.success(false); // fails to reserve
        when(productCatalogClient.reserveStock("prod_1", 2)).thenReturn(successResponse);
        when(productCatalogClient.reserveStock("prod_2", 1)).thenReturn(failResponse);

        // Act & Assert
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            orderService.createOrder(createOrderRequest);
        });

        assertTrue(ex.getMessage().contains("Insufficient stock for product: Product 2"));

        // Verify reserve was called
        verify(productCatalogClient, times(1)).reserveStock("prod_1", 2);
        verify(productCatalogClient, times(1)).reserveStock("prod_2", 1);

        // Verify compensation release was called for item 1 but NOT item 2
        verify(productCatalogClient, times(1)).releaseStock("prod_1", 2);
        verify(productCatalogClient, never()).releaseStock(eq("prod_2"), anyInt());

        // Verify order was not saved
        verify(orderRepository, never()).save(any(Order.class));
    }
}
