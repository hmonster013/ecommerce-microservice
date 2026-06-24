package org.de013.paymentservice.mapper;

import org.de013.paymentservice.dto.payment.PaymentResponse;
import org.de013.paymentservice.dto.paymentmethod.PaymentMethodResponse;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.enums.Currency;
import org.de013.paymentservice.entity.enums.PaymentMethodType;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.de013.paymentservice.service.PaymentMethodService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentMapperTest {

    @Mock
    private PaymentMethodService paymentMethodService;

    @InjectMocks
    private PaymentMapper paymentMapper;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @Test
    void toPaymentResponse_WhenPaymentMethodIdPresent_ShouldPopulatePaymentMethodInfo() {
        // Regression R3 paymentMethodInfo
        Payment payment = Payment.builder()
                .id(1L)
                .paymentNumber("PAY-1001")
                .orderId(123L)
                .userId("user-123")
                .amount(BigDecimal.valueOf(100.00))
                .currency(Currency.USD)
                .status(PaymentStatus.SUCCEEDED)
                .method(PaymentMethodType.CARD)
                .stripePaymentMethodId("pm_test_card")
                .build();

        PaymentMethodResponse mockPm = PaymentMethodResponse.builder()
                .id(1L)
                .type(PaymentMethodType.CARD)
                .cardInfo(PaymentMethodResponse.CardInfo.builder()
                        .brand("Visa")
                        .maskedNumber("************1111")
                        .expiryMonth(12)
                        .expiryYear(2030)
                        .country("US")
                        .funding("credit")
                        .build())
                .build();

        when(paymentMethodService.getPaymentMethodByStripeId("pm_test_card")).thenReturn(Optional.of(mockPm));

        PaymentResponse response = paymentMapper.toPaymentResponse(payment);

        assertNotNull(response);
        assertNotNull(response.getPaymentMethodInfo());
        assertEquals("Visa", response.getPaymentMethodInfo().getBrand());
        assertEquals("1111", response.getPaymentMethodInfo().getLast4());
        assertEquals(12, response.getPaymentMethodInfo().getExpiryMonth());
        assertEquals(2030, response.getPaymentMethodInfo().getExpiryYear());
        assertEquals("US", response.getPaymentMethodInfo().getCountry());
        assertEquals("credit", response.getPaymentMethodInfo().getFunding());
    }

    @Test
    void toPaymentResponse_WhenPaymentMethodIdNull_ShouldNotPopulatePaymentMethodInfo() {
        Payment payment = Payment.builder()
                .id(1L)
                .paymentNumber("PAY-1001")
                .orderId(123L)
                .userId("user-123")
                .amount(BigDecimal.valueOf(100.00))
                .currency(Currency.USD)
                .status(PaymentStatus.SUCCEEDED)
                .method(PaymentMethodType.CARD)
                .stripePaymentMethodId(null)
                .build();

        PaymentResponse response = paymentMapper.toPaymentResponse(payment);

        assertNotNull(response);
        assertNull(response.getPaymentMethodInfo());
    }
}
