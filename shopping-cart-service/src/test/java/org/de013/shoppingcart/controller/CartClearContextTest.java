package org.de013.shoppingcart.controller;

import org.de013.shoppingcart.dto.response.CartResponseDto;
import org.de013.shoppingcart.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CartClearContextTest {
    @Mock CartService carts;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CartController(carts)).build();
    }

    @Test
    void clearsAuthenticatedCartWithoutUserIdQueryAndIgnoresGuestSession() throws Exception {
        when(carts.clearCart("current-user", null)).thenReturn(emptyCart());
        mvc.perform(delete("/carts/clear")
                        .header("X-User-Id", "current-user").header("X-User-Username", "customer")
                        .param("sessionId", "unrelated-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cart_id").value(42))
                .andExpect(jsonPath("$.data.total_amount").value(0));
        verify(carts).clearCart("current-user", null);
    }

    @Test
    void rejectsAnotherUsersIdBeforeCallingTheService() throws Exception {
        mvc.perform(delete("/carts/clear").param("userId", "someone-else")
                        .header("X-User-Id", "current-user").header("X-User-Username", "customer"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(carts);
    }

    @Test
    void anonymousCallerCannotSelectAUserCartByQuery() throws Exception {
        mvc.perform(delete("/carts/clear").param("userId", "someone-else"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(carts);
    }

    @Test
    void missingIdentityDoesNotClearAnyCart() throws Exception {
        mvc.perform(delete("/carts/clear")).andExpect(status().isBadRequest());
        verifyNoInteractions(carts);
    }

    @Test
    void keepsTheExistingGuestSessionContract() throws Exception {
        when(carts.clearCart(null, "guest-session")).thenReturn(emptyCart());
        mvc.perform(delete("/carts/clear").param("sessionId", "guest-session"))
                .andExpect(status().isOk());
        verify(carts).clearCart(null, "guest-session");
    }

    private CartResponseDto emptyCart() {
        return CartResponseDto.builder().cartId(42L).items(List.of())
                .subtotal(BigDecimal.ZERO).totalAmount(BigDecimal.ZERO).build();
    }
}
