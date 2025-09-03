package org.de013.paymentservice.gateway.stripe;

import com.stripe.model.Customer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.dto.stripe.StripeCustomerResponse;
import org.springframework.stereotype.Service;

/**
 * Service for Stripe customer operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeCustomerService {

    /**
     * Map Stripe Customer to StripeCustomerResponse
     */
    public StripeCustomerResponse mapToCustomerResponse(Customer customer) {
        if (customer == null) {
            return null;
        }

        StripeCustomerResponse.StripeCustomerResponseBuilder builder = StripeCustomerResponse.builder()
                .customerId(customer.getId())
                .email(customer.getEmail())
                .name(customer.getName())
                .phone(customer.getPhone())
                .description(customer.getDescription())
                .created(customer.getCreated())
                .livemode(customer.getLivemode())
                .balance(customer.getBalance())
                .currency(customer.getCurrency());

        // Map address if present
        if (customer.getAddress() != null) {
            builder.address(StripeCustomerResponse.StripeAddress.builder()
                    .line1(customer.getAddress().getLine1())
                    .line2(customer.getAddress().getLine2())
                    .city(customer.getAddress().getCity())
                    .state(customer.getAddress().getState())
                    .postalCode(customer.getAddress().getPostalCode())
                    .country(customer.getAddress().getCountry())
                    .build());
        }

        // Map shipping if present
        if (customer.getShipping() != null) {
            StripeCustomerResponse.StripeAddress shippingAddress = null;
            if (customer.getShipping().getAddress() != null) {
                shippingAddress = StripeCustomerResponse.StripeAddress.builder()
                        .line1(customer.getShipping().getAddress().getLine1())
                        .line2(customer.getShipping().getAddress().getLine2())
                        .city(customer.getShipping().getAddress().getCity())
                        .state(customer.getShipping().getAddress().getState())
                        .postalCode(customer.getShipping().getAddress().getPostalCode())
                        .country(customer.getShipping().getAddress().getCountry())
                        .build();
            }

            builder.shipping(StripeCustomerResponse.StripeShipping.builder()
                    .name(customer.getShipping().getName())
                    .phone(customer.getShipping().getPhone())
                    .address(shippingAddress)
                    .build());
        }

        // Map invoice settings if present
        if (customer.getInvoiceSettings() != null) {
            builder.invoiceSettings(StripeCustomerResponse.StripeInvoiceSettings.builder()
                    .defaultPaymentMethod(customer.getInvoiceSettings().getDefaultPaymentMethod())
                    .footer(customer.getInvoiceSettings().getFooter())
                    .build());
        }

        // Map metadata
        if (customer.getMetadata() != null) {
            builder.metadata(customer.getMetadata());
        }

        return builder.build();
    }
}
