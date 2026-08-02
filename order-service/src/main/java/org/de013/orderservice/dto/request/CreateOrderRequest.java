package org.de013.orderservice.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.de013.orderservice.entity.enums.OrderType;
import org.de013.orderservice.entity.valueobject.Address;

/**
 * Create Order Request DTO - Simplified Base Version
 */
@JsonIgnoreProperties(value = "userId", allowGetters = true)
public record CreateOrderRequest(
        @Schema(hidden = true)
        String userId,

        // ID of the shopping cart to convert to order
        @Schema(description = "ID of the shopping cart to convert to order", example = "1")
        @NotNull(message = "{cart.id.required}")
        @Positive(message = "{cart.id.positive}")
        Long cartId,

        // Type of order being placed
        @Schema(description = "Type of order being placed", example = "STANDARD")
        @NotNull(message = "{order.type.required}")
        OrderType orderType,

        // Shipping address for the order
        @Valid
        @NotNull(message = "{shipping.address.required}")
        Address shippingAddress,

        // Billing address for the order (optional, defaults to shipping address)
        @Valid
        Address billingAddress,

        // Payment method type (simplified for base version)
        @Schema(description = "Payment method for the order", example = "CREDIT_CARD")
        @NotBlank(message = "Payment method is required")
        @Size(max = 50, message = "Payment method must not exceed 50 characters")
        String paymentMethod,

        // Currency for the order
        @Schema(description = "Currency code for the order", example = "USD")
        @NotBlank(message = "{currency.required}")
        @Size(min = 3, max = 3, message = "{currency.size}")
        @Pattern(regexp = "^[A-Z]{3}$", message = "{currency.format}")
        String currency,

        // Customer notes for the order (optional)
        @Schema(description = "Optional customer notes for the order", example = "Please deliver after 6 PM")
        @Size(max = 500, message = "Customer notes must not exceed 500 characters")
        String customerNotes
) {

    /**
     * Get effective billing address (shipping address if billing is null)
     * Hidden from Swagger documentation
     */
    @JsonIgnore
    public Address getEffectiveBillingAddress() {
        return billingAddress != null ? billingAddress : shippingAddress;
    }

    @JsonCreator
    public CreateOrderRequest(
            @JsonProperty("cart_id") @JsonAlias("cartId") Long cartId,
            @JsonProperty("order_type") @JsonAlias("orderType") OrderType orderType,
            @JsonProperty("shipping_address") @JsonAlias("shippingAddress") Address shippingAddress,
            @JsonProperty("billing_address") @JsonAlias("billingAddress") Address billingAddress,
            @JsonProperty("payment_method") @JsonAlias("paymentMethod") String paymentMethod,
            @JsonProperty("currency") String currency,
            @JsonProperty("customer_notes") @JsonAlias("customerNotes") String customerNotes
    ) {
        this(null, cartId, orderType, shippingAddress, billingAddress, paymentMethod, currency, customerNotes);
    }

    public CreateOrderRequest withUserId(String userId) {
        return new CreateOrderRequest(
                userId,
                cartId,
                orderType,
                shippingAddress,
                billingAddress,
                paymentMethod,
                currency,
                customerNotes
        );
    }
}
