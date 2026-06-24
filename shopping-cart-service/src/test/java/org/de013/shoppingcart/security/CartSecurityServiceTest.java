package org.de013.shoppingcart.security;

import org.de013.shoppingcart.dto.response.CartResponseDto;
import org.de013.shoppingcart.repository.jpa.CartItemRepository;
import org.de013.shoppingcart.service.CartService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class CartSecurityServiceTest {

    @Mock
    private CartService cartService;

    @Mock
    private CartItemRepository cartItemRepository;

    @InjectMocks
    private CartSecurityService cartSecurityService;

    private MockHttpServletRequest request;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() throws Exception {
        RequestContextHolder.resetRequestAttributes();
        mocks.close();
    }

    @Test
    void canAccessCart_WhenNoUserContext_ShouldDeny() {
        boolean result = cartSecurityService.canAccessCart(1L);
        assertFalse(result);
    }

    @Test
    void canAccessCart_WhenUserIsOwner_ShouldGrant() {
        // Mock user headers
        request.addHeader("X-User-Id", "user-uuid-abc");
        request.addHeader("X-User-Username", "john_owner");

        // Mock cart service response
        CartResponseDto cartDto = CartResponseDto.builder()
                .cartId(1L)
                .userId("user-uuid-abc")
                .build();
        when(cartService.getCartById(1L)).thenReturn(Optional.of(cartDto));

        boolean result = cartSecurityService.canAccessCart(1L);
        assertTrue(result);
    }

    @Test
    void canAccessCart_WhenUserIsNotOwner_ShouldDeny() {
        // Mock user headers (intruder)
        request.addHeader("X-User-Id", "intruder-uuid");
        request.addHeader("X-User-Username", "intruder");

        // Mock cart service response owned by someone else (Regression R7 IDOR)
        CartResponseDto cartDto = CartResponseDto.builder()
                .cartId(1L)
                .userId("user-uuid-abc")
                .build();
        when(cartService.getCartById(1L)).thenReturn(Optional.of(cartDto));

        boolean result = cartSecurityService.canAccessCart(1L);
        assertFalse(result);
    }

    @Test
    void canAccessCartByUserId_WhenOwnCart_ShouldGrant() {
        request.addHeader("X-User-Id", "user-uuid-abc");
        request.addHeader("X-User-Username", "john_owner");

        boolean result = cartSecurityService.canAccessCart("user-uuid-abc");
        assertTrue(result);
    }

    @Test
    void canAccessCartByUserId_WhenDifferentCart_ShouldDeny() {
        request.addHeader("X-User-Id", "intruder-uuid");
        request.addHeader("X-User-Username", "intruder");

        boolean result = cartSecurityService.canAccessCart("user-uuid-abc");
        assertFalse(result);
    }
}
