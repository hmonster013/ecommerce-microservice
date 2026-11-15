package org.de013.paymentservice.service;

import org.de013.paymentservice.client.OrderServiceClient;
import org.de013.paymentservice.client.UserServiceClient;
import org.de013.paymentservice.dto.external.OrderDto;
import org.de013.paymentservice.dto.external.UserValidationResponse;
import org.de013.paymentservice.dto.payment.ProcessPaymentRequest;
import org.de013.common.exception.ConflictException;
import org.de013.paymentservice.entity.enums.Currency;
import org.de013.paymentservice.exception.PaymentProcessingException;
import org.de013.paymentservice.service.validation.PaymentRequestValidator;
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
    private PaymentRequestValidator validator;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @Test
    void validatePaymentRequest_ChecksOrderIdentityAndOwnerBeforeUserEligibility() {
        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
                .orderId(123L).userId("user-abc").amount(BigDecimal.TEN).currency(Currency.USD).build();
        OrderDto order = new OrderDto();
        order.setId(123L);
        order.setUserId("user-abc");
        OrderDto.MoneyDto money = new OrderDto.MoneyDto();
        money.setAmount(BigDecimal.TEN);
        money.setCurrency("USD");
        order.setTotalAmount(money);
        when(orderServiceClient.getOrderById(123L)).thenReturn(ResponseEntity.ok(order));
        when(userServiceClient.validateUserForPayment("user-abc")).thenReturn(ResponseEntity.ok(
                UserValidationResponse.builder().valid(true).canMakePayments(true).build()));

        assertDoesNotThrow(() -> validator.validatePaymentRequest(request));
        verify(orderServiceClient, times(1)).getOrderById(123L);
        verify(userServiceClient, times(1)).validateUserForPayment("user-abc");
        clearInvocations(userServiceClient);

        for (String owner : new String[]{"other-user", null, "", "  "}) {
            order.setUserId(owner);
            assertThrows(PaymentProcessingException.class, () -> validator.validatePaymentRequest(request));
        }
        order.setUserId("user-abc");
        for (Long id : new Long[]{456L, null}) {
            order.setId(id);
            assertThrows(PaymentProcessingException.class, () -> validator.validatePaymentRequest(request));
        }
        verifyNoInteractions(userServiceClient);
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

        assertDoesNotThrow(() -> validator.validatePaymentAmount(orderId, amount));
    }

    @Test
    void validatePaymentAmount_WhenAmountsAndCurrencyMatch_ShouldSucceed() {
        Long orderId = 123L;
        BigDecimal amount = BigDecimal.valueOf(100.00);

        OrderDto orderDto = new OrderDto();
        OrderDto.MoneyDto money = new OrderDto.MoneyDto();
        money.setAmount(BigDecimal.valueOf(100.00));
        money.setCurrency("USD");
        orderDto.setTotalAmount(money);

        when(orderServiceClient.getOrderById(orderId)).thenReturn(ResponseEntity.ok(orderDto));

        assertDoesNotThrow(() -> validator.validatePaymentAmount(orderId, amount, Currency.USD));
    }

    @Test
    void validatePaymentAmount_WhenCurrencyMismatch_ShouldThrowException() {
        Long orderId = 123L;
        BigDecimal amount = BigDecimal.valueOf(100.00);

        OrderDto orderDto = new OrderDto();
        OrderDto.MoneyDto money = new OrderDto.MoneyDto();
        money.setAmount(BigDecimal.valueOf(100.00));
        money.setCurrency("USD");
        orderDto.setTotalAmount(money);

        when(orderServiceClient.getOrderById(orderId)).thenReturn(ResponseEntity.ok(orderDto));

        assertThrows(PaymentProcessingException.class, () ->
                validator.validatePaymentAmount(orderId, amount, Currency.VND));
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
                validator.validatePaymentAmount(orderId, amount));
    }

    @Test
    void validatePaymentAmount_WhenOrderAlreadyPaid_ShouldThrowException() {
        Long orderId = 123L;
        BigDecimal amount = BigDecimal.valueOf(100.00);

        OrderDto orderDto = new OrderDto();
        orderDto.setStatus("PAID");
        OrderDto.MoneyDto money = new OrderDto.MoneyDto();
        money.setAmount(BigDecimal.valueOf(100.00));
        orderDto.setTotalAmount(money);

        when(orderServiceClient.getOrderById(orderId)).thenReturn(ResponseEntity.ok(orderDto));

        assertThrows(ConflictException.class, () ->
                validator.validatePaymentAmount(orderId, amount));
    }

    @Test
    void validatePaymentAmount_WhenOrderCancelled_ShouldThrowException() {
        Long orderId = 123L;
        BigDecimal amount = BigDecimal.valueOf(100.00);

        OrderDto orderDto = new OrderDto();
        orderDto.setStatus("CANCELLED");
        OrderDto.MoneyDto money = new OrderDto.MoneyDto();
        money.setAmount(BigDecimal.valueOf(100.00));
        orderDto.setTotalAmount(money);

        when(orderServiceClient.getOrderById(orderId)).thenReturn(ResponseEntity.ok(orderDto));

        assertThrows(ConflictException.class, () ->
                validator.validatePaymentAmount(orderId, amount));
    }

    @Test
    void validateUserCanMakePayment_WhenUserIsAllowed_ShouldSucceed() {
        String userId = "user-abc";

        UserValidationResponse userResponse = UserValidationResponse.builder()
                .valid(true)
                .canMakePayments(true)
                .build();

        when(userServiceClient.validateUserForPayment(userId)).thenReturn(ResponseEntity.ok(userResponse));

        assertDoesNotThrow(() -> validator.validateUserCanMakePayment(userId));
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
                validator.validateUserCanMakePayment(userId));
    }

    @Test
    void validateUserCanMakePayment_WhenUserServiceFails_ShouldThrowException() {
        String userId = "user-abc";

        when(userServiceClient.validateUserForPayment(userId)).thenThrow(new RuntimeException("Connection refused"));

        assertThrows(PaymentProcessingException.class, () -> 
                validator.validateUserCanMakePayment(userId));
    }
}
