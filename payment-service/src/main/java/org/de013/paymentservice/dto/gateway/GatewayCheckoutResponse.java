package org.de013.paymentservice.dto.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for checkout responses across different payment gateways
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayCheckoutResponse {
    private String type; // e.g., "REDIRECT"
    private String redirectUrl;
    private String gatewayTxnRef;
    private String status; // e.g., "PENDING"
}
