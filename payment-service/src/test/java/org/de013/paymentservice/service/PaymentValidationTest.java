package org.de013.paymentservice.service;

import org.de013.paymentservice.client.OrderServiceClient;
import org.de013.paymentservice.client.UserServiceClient;
import org.de013.paymentservice.dto.external.OrderDto;
import org.de013.paymentservice.dto.external.UserValidationResponse;
import org.de013.paymentservice.exception.PaymentProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentValidationTest {

    @Mock
    private OrderServiceClient orderServiceClient;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @Test
    void validatePaymentAmount_WhenAmountsMatch_ShouldSucceed() {
        Long orderId = 123L;
        BigDecimal amount = BigDecimal.valueOf(100.00);

        OrderDto orderDto = new OrderDto();
        OrderDto.MoneyDto money = new OrderDto.MoneyDto();
        money.setAmount(BigDecimal.valueOf(100.00));
        orderDto.setTotalAmount(money);

        when(orderServiceClient.getOrderById(orderId)).thenReturn(ResponseEntity.ok(orderDto));

        assertDoesNotThrow(() -> paymentService.validatePaymentAmount(orderId, amount));
    }

    @Test
    void validatePaymentAmount_WhenAmountsMismatch_ShouldThrowException() {
        // Regression S1 amount tampering protection
        Long orderId = 123L;
        BigDecimal amount = BigDecimal.valueOf(150.00); // tampered amount

        OrderDto orderDto = new OrderDto();
        OrderDto.MoneyDto money = new OrderDto.MoneyDto();
        money.setAmount(BigDecimal.valueOf(100.00)); // real amount
        orderDto.setTotalAmount(money);

        when(orderServiceClient.getOrderById(orderId)).thenReturn(ResponseEntity.ok(orderDto));

        assertThrows(PaymentProcessingException.class, () -> 
                paymentService.validatePaymentAmount(orderId, amount));
    }

    @Test
    void validateUserCanMakePayment_WhenUserIsAllowed_ShouldSucceed() {
        String userId = "user-abc";

        UserValidationResponse userResponse = UserValidationResponse.builder()
                .valid(true)
                .canMakePayments(true)
                .build();

        when(userServiceClient.validateUserForPayment(userId)).thenReturn(ResponseEntity.ok(userResponse));

        assertDoesNotThrow(() -> paymentService.validateUserCanMakePayment(userId));
    }

    @Test
    void validateUserCanMakePayment_WhenUserIsBlocked_ShouldThrowException() {
        // Regression S2 blocked user check
        String userId = "user-blocked";

        UserValidationResponse userResponse = UserValidationResponse.builder()
                .valid(false)
                .canMakePayments(false)
                .paymentBlockReason("HIGH_RISK")
                .build();

        when(userServiceClient.validateUserForPayment(userId)).thenReturn(ResponseEntity.ok(userResponse));

        assertThrows(PaymentProcessingException.class, () -> 
                paymentService.validateUserCanMakePayment(userId));
    }
}
