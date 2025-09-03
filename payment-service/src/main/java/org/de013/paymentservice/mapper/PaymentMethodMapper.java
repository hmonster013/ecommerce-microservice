package org.de013.paymentservice.mapper;

import org.de013.paymentservice.dto.paymentmethod.PaymentMethodResponse;
import org.de013.paymentservice.entity.PaymentMethod;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;

/**
 * Mapper for converting PaymentMethod entities to DTOs
 */
@Component
public class PaymentMethodMapper {

    /**
     * Convert PaymentMethod entity to PaymentMethodResponse DTO
     */
    public PaymentMethodResponse toPaymentMethodResponse(PaymentMethod paymentMethod) {
        if (paymentMethod == null) {
            return null;
        }

        PaymentMethodResponse.PaymentMethodResponseBuilder builder = PaymentMethodResponse.builder()
                .id(paymentMethod.getId())
                .userId(paymentMethod.getUserId())
                .type(paymentMethod.getType())
                .provider(paymentMethod.getProvider())
                .nickname(paymentMethod.getNickname())
                .isActive(paymentMethod.getIsActive())
                .isDefault(paymentMethod.getIsDefault())
                .lastUsedAt(paymentMethod.getLastUsedAt())
                .createdAt(paymentMethod.getCreatedAt())
                .updatedAt(paymentMethod.getUpdatedAt());

        // Map card details if present
        if (paymentMethod.getType() == org.de013.paymentservice.entity.enums.PaymentMethodType.CARD) {
            builder.cardInfo(PaymentMethodResponse.CardInfo.builder()
                    .brand(paymentMethod.getCardBrand())
                    .maskedNumber(paymentMethod.getMaskedCardNumber())
                    .expiryMonth(paymentMethod.getExpiryMonth())
                    .expiryYear(paymentMethod.getExpiryYear())
                    .country(paymentMethod.getCardCountry())
                    .funding(paymentMethod.getCardFunding())
                    .isExpired(isCardExpired(paymentMethod.getExpiryMonth(), paymentMethod.getExpiryYear()))
                    .build());
        }

        // Map billing address if present
        if (paymentMethod.getCustomerName() != null || paymentMethod.getBillingAddressLine1() != null) {
            builder.billingAddress(PaymentMethodResponse.BillingAddress.builder()
                    .customerName(paymentMethod.getCustomerName())
                    .line1(paymentMethod.getBillingAddressLine1())
                    .line2(paymentMethod.getBillingAddressLine2())
                    .city(paymentMethod.getBillingCity())
                    .state(paymentMethod.getBillingState())
                    .postalCode(paymentMethod.getBillingPostalCode())
                    .country(paymentMethod.getBillingCountry())
                    .build());
        }

        return builder.build();
    }

    /**
     * Convert list of PaymentMethod entities to PaymentMethodResponse DTOs
     */
    public List<PaymentMethodResponse> toPaymentMethodResponseList(List<PaymentMethod> paymentMethods) {
        if (paymentMethods == null || paymentMethods.isEmpty()) {
            return List.of();
        }

        return paymentMethods.stream()
                .map(this::toPaymentMethodResponse)
                .toList();
    }

    /**
     * Check if card is expired
     */
    private boolean isCardExpired(Integer expiryMonth, Integer expiryYear) {
        if (expiryMonth == null || expiryYear == null) {
            return false;
        }

        try {
            YearMonth cardExpiry = YearMonth.of(expiryYear, expiryMonth);
            YearMonth currentMonth = YearMonth.now();
            return cardExpiry.isBefore(currentMonth);
        } catch (Exception e) {
            return false;
        }
    }
}
