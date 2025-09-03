package org.de013.paymentservice.gateway.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentMethod;
import com.stripe.model.PaymentMethodCollection;
import com.stripe.param.PaymentMethodAttachParams;
import com.stripe.param.PaymentMethodCreateParams;
import com.stripe.param.PaymentMethodListParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.dto.stripe.StripePaymentMethodRequest;
import org.de013.paymentservice.dto.stripe.StripePaymentMethodResponse;
import org.de013.paymentservice.exception.PaymentGatewayException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for Stripe payment method operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripePaymentMethodService {

    /**
     * Create a payment method
     */
    public StripePaymentMethodResponse createPaymentMethod(StripePaymentMethodRequest request) throws Exception {
        try {
            PaymentMethodCreateParams.Builder paramsBuilder = PaymentMethodCreateParams.builder()
                    .setType(PaymentMethodCreateParams.Type.valueOf(request.getType().toUpperCase()));

            // Add card details if provided
            if (request.isCardPaymentMethod() && request.hasCardDetails()) {
                PaymentMethodCreateParams.CardDetails cardDetails = PaymentMethodCreateParams.CardDetails.builder()
                        .setNumber(request.getCard().getNumber())
                        .setExpMonth(Long.valueOf(request.getCard().getExpMonth()))
                        .setExpYear(Long.valueOf(request.getCard().getExpYear()))
                        .setCvc(request.getCard().getCvc())
                        .build();
                paramsBuilder.setCard(cardDetails);
            }

            // Add billing details if provided
            if (request.hasBillingDetails()) {
                PaymentMethodCreateParams.BillingDetails.Builder billingBuilder = 
                        PaymentMethodCreateParams.BillingDetails.builder();
                
                if (request.getBillingDetails().getName() != null) {
                    billingBuilder.setName(request.getBillingDetails().getName());
                }
                if (request.getBillingDetails().getEmail() != null) {
                    billingBuilder.setEmail(request.getBillingDetails().getEmail());
                }
                if (request.getBillingDetails().getPhone() != null) {
                    billingBuilder.setPhone(request.getBillingDetails().getPhone());
                }

                // Add address if provided
                if (request.getBillingDetails().getAddress() != null) {
                    PaymentMethodCreateParams.BillingDetails.Address address = 
                            PaymentMethodCreateParams.BillingDetails.Address.builder()
                                    .setLine1(request.getBillingDetails().getAddress().getLine1())
                                    .setLine2(request.getBillingDetails().getAddress().getLine2())
                                    .setCity(request.getBillingDetails().getAddress().getCity())
                                    .setState(request.getBillingDetails().getAddress().getState())
                                    .setPostalCode(request.getBillingDetails().getAddress().getPostalCode())
                                    .setCountry(request.getBillingDetails().getAddress().getCountry())
                                    .build();
                    billingBuilder.setAddress(address);
                }

                paramsBuilder.setBillingDetails(billingBuilder.build());
            }

            PaymentMethod paymentMethod = PaymentMethod.create(paramsBuilder.build());
            return mapToPaymentMethodResponse(paymentMethod);

        } catch (StripeException e) {
            log.error("Failed to create Stripe payment method", e);
            throw new PaymentGatewayException("Failed to create payment method: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieve payment method
     */
    public StripePaymentMethodResponse getPaymentMethod(String paymentMethodId) throws Exception {
        try {
            PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
            return mapToPaymentMethodResponse(paymentMethod);
        } catch (StripeException e) {
            log.error("Failed to retrieve Stripe payment method: {}", paymentMethodId, e);
            throw new PaymentGatewayException("Failed to retrieve payment method: " + e.getMessage(), e);
        }
    }

    /**
     * Attach payment method to customer
     */
    public StripePaymentMethodResponse attachPaymentMethod(String paymentMethodId, String customerId) throws Exception {
        try {
            PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
            PaymentMethodAttachParams params = PaymentMethodAttachParams.builder()
                    .setCustomer(customerId)
                    .build();
            paymentMethod = paymentMethod.attach(params);
            return mapToPaymentMethodResponse(paymentMethod);
        } catch (StripeException e) {
            log.error("Failed to attach Stripe payment method {} to customer {}", paymentMethodId, customerId, e);
            throw new PaymentGatewayException("Failed to attach payment method: " + e.getMessage(), e);
        }
    }

    /**
     * Detach payment method from customer
     */
    public StripePaymentMethodResponse detachPaymentMethod(String paymentMethodId) throws Exception {
        try {
            PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
            paymentMethod = paymentMethod.detach();
            return mapToPaymentMethodResponse(paymentMethod);
        } catch (StripeException e) {
            log.error("Failed to detach Stripe payment method: {}", paymentMethodId, e);
            throw new PaymentGatewayException("Failed to detach payment method: " + e.getMessage(), e);
        }
    }

    /**
     * List customer's payment methods
     */
    public List<StripePaymentMethodResponse> listPaymentMethods(String customerId, String type) throws Exception {
        try {
            PaymentMethodListParams.Builder paramsBuilder = PaymentMethodListParams.builder()
                    .setCustomer(customerId);
            
            if (type != null) {
                paramsBuilder.setType(PaymentMethodListParams.Type.valueOf(type.toUpperCase()));
            }

            PaymentMethodCollection paymentMethods = PaymentMethod.list(paramsBuilder.build());
            
            return paymentMethods.getData().stream()
                    .map(this::mapToPaymentMethodResponse)
                    .toList();
                    
        } catch (StripeException e) {
            log.error("Failed to list Stripe payment methods for customer: {}", customerId, e);
            throw new PaymentGatewayException("Failed to list payment methods: " + e.getMessage(), e);
        }
    }

    /**
     * Update payment method
     */
    public StripePaymentMethodResponse updatePaymentMethod(String paymentMethodId, StripePaymentMethodRequest request) throws Exception {
        try {
            // Note: Stripe has limited update capabilities for payment methods
            // Most updates require creating a new payment method
            PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
            return mapToPaymentMethodResponse(paymentMethod);
        } catch (StripeException e) {
            log.error("Failed to update Stripe payment method: {}", paymentMethodId, e);
            throw new PaymentGatewayException("Failed to update payment method: " + e.getMessage(), e);
        }
    }

    /**
     * Map Stripe PaymentMethod to StripePaymentMethodResponse
     */
    public StripePaymentMethodResponse mapToPaymentMethodResponse(PaymentMethod paymentMethod) {
        if (paymentMethod == null) {
            return null;
        }

        StripePaymentMethodResponse.StripePaymentMethodResponseBuilder builder = StripePaymentMethodResponse.builder()
                .paymentMethodId(paymentMethod.getId())
                .type(paymentMethod.getType())
                .customerId(paymentMethod.getCustomer())
                .created(paymentMethod.getCreated())
                .livemode(paymentMethod.getLivemode());

        // Map card details if present
        if (paymentMethod.getCard() != null) {
            StripePaymentMethodResponse.StripeChecks checks = null;
            if (paymentMethod.getCard().getChecks() != null) {
                checks = StripePaymentMethodResponse.StripeChecks.builder()
                        .addressLine1Check(paymentMethod.getCard().getChecks().getAddressLine1Check())
                        .addressPostalCodeCheck(paymentMethod.getCard().getChecks().getAddressPostalCodeCheck())
                        .cvcCheck(paymentMethod.getCard().getChecks().getCvcCheck())
                        .build();
            }

            builder.card(StripePaymentMethodResponse.StripeCard.builder()
                    .brand(paymentMethod.getCard().getBrand())
                    .country(paymentMethod.getCard().getCountry())
                    .expMonth(Math.toIntExact(paymentMethod.getCard().getExpMonth()))
                    .expYear(Math.toIntExact(paymentMethod.getCard().getExpYear()))
                    .funding(paymentMethod.getCard().getFunding())
                    .last4(paymentMethod.getCard().getLast4())
                    .network(paymentMethod.getCard().getNetwork())
                    .checks(checks)
                    .wallet(paymentMethod.getCard().getWallet() != null ? 
                            paymentMethod.getCard().getWallet().getType() : null)
                    .build());
        }

        // Map billing details if present
        if (paymentMethod.getBillingDetails() != null) {
            StripePaymentMethodResponse.StripeAddress address = null;
            if (paymentMethod.getBillingDetails().getAddress() != null) {
                address = StripePaymentMethodResponse.StripeAddress.builder()
                        .line1(paymentMethod.getBillingDetails().getAddress().getLine1())
                        .line2(paymentMethod.getBillingDetails().getAddress().getLine2())
                        .city(paymentMethod.getBillingDetails().getAddress().getCity())
                        .state(paymentMethod.getBillingDetails().getAddress().getState())
                        .postalCode(paymentMethod.getBillingDetails().getAddress().getPostalCode())
                        .country(paymentMethod.getBillingDetails().getAddress().getCountry())
                        .build();
            }

            builder.billingDetails(StripePaymentMethodResponse.StripeBillingDetails.builder()
                    .name(paymentMethod.getBillingDetails().getName())
                    .email(paymentMethod.getBillingDetails().getEmail())
                    .phone(paymentMethod.getBillingDetails().getPhone())
                    .address(address)
                    .build());
        }

        // Map metadata
        if (paymentMethod.getMetadata() != null) {
            builder.metadata(paymentMethod.getMetadata());
        }

        return builder.build();
    }
}
