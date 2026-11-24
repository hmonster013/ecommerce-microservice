package org.de013.paymentservice.dto.gateway;

/**
 * DTO for checkout responses across different payment gateways
 */
public record GatewayCheckoutResponse(
        String type, // e.g., "REDIRECT"
        String redirectUrl,
        String gatewayTxnRef,
        String status // e.g., "PENDING"
) {
}
