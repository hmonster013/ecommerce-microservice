package org.de013.orderservice.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import org.de013.orderservice.entity.enums.OrderType;
import org.de013.orderservice.entity.valueobject.Address;

/**
 * Create Order Request DTO - Simplified Base Version
 *
 * Request object for creating a new order from shopping cart.
 * Contains only essential information for basic order creation.
 *
 * @author Development Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {

    /**
     * ID of the user placing the order
     */
    @Schema(description = "ID of the user placing the order", example = "1")
    @NotNull(message = "{user.id.required}")
    @Positive(message = "{user.id.positive}")
    private Long userId;

    /**
     * ID of the shopping cart to convert to order
     */
    @Schema(description = "ID of the shopping cart to convert to order", example = "1")
    @NotNull(message = "{cart.id.required}")
    @Positive(message = "{cart.id.positive}")
    private Long cartId;

    /**
     * Type of order being placed
     */
    @Schema(description = "Type of order being placed", example = "STANDARD")
    @NotNull(message = "{order.type.required}")
    private OrderType orderType;

    /**
     * Shipping address for the order
     */
    @Valid
    @NotNull(message = "{shipping.address.required}")
    private Address shippingAddress;

    /**
     * Billing address for the order (optional, defaults to shipping address)
     */
    @Valid
    private Address billingAddress;

    /**
     * Payment method type (simplified for base version)
     */
    @Schema(description = "Payment method for the order", example = "CREDIT_CARD")
    @NotBlank(message = "Payment method is required")
    @Size(max = 50, message = "Payment method must not exceed 50 characters")
    private String paymentMethod;

    /**
     * Currency for the order
     */
    @Schema(description = "Currency code for the order", example = "USD")
    @NotBlank(message = "{currency.required}")
    @Size(min = 3, max = 3, message = "{currency.size}")
    @Pattern(regexp = "^[A-Z]{3}$", message = "{currency.format}")
    private String currency;

    /**
     * Customer notes for the order (optional)
     */
    @Schema(description = "Optional customer notes for the order", example = "Please deliver after 6 PM")
    @Size(max = 500, message = "Customer notes must not exceed 500 characters")
    private String customerNotes;
    



    /**
     * Get effective billing address (shipping address if billing is null)
     * Hidden from Swagger documentation
     */
    @JsonIgnore
    public Address getEffectiveBillingAddress() {
        return billingAddress != null ? billingAddress : shippingAddress;
    }



}
