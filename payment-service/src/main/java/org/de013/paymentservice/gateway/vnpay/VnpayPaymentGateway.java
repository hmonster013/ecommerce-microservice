package org.de013.paymentservice.gateway.vnpay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.config.PaymentGatewayConfig;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Standard Component for VNPay Payment Gateway integration.
 * Performs payment URL construction and IPN/Return verification.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VnpayPaymentGateway {

    private final PaymentGatewayConfig config;

    public String getProviderName() {
        return "VNPAY";
    }

    public boolean isEnabled() {
        return config.getGateways().getVnpay().isEnabled();
    }

    /**
     * Builds the VNPAY payment redirect URL
     * 
     * @param txnRef     Unique transaction reference (e.g. payment number)
     * @param amountVnd  Amount in VND (not multiplied by 100 yet)
     * @param orderInfo  Order description / info
     * @param ipAddr     IP address of the client
     * @param createDate Creation timestamp (yyyyMMddHHmmss, GMT+7)
     * @return Full VNPAY payment URL with secure hash signature
     */
    public String buildPaymentUrl(String txnRef, long amountVnd, String orderInfo, String ipAddr, String createDate) {
        PaymentGatewayConfig.Vnpay vnpayConfig = config.getGateways().getVnpay();
        
        SortedMap<String, String> fields = new TreeMap<>();
        fields.put("vnp_Version", vnpayConfig.getVersion());
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", vnpayConfig.getTmnCode());
        fields.put("vnp_Amount", String.valueOf(amountVnd * 100)); // VNPay expects amount * 100
        fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_TxnRef", txnRef);
        fields.put("vnp_OrderInfo", orderInfo);
        fields.put("vnp_OrderType", "other");
        fields.put("vnp_Locale", "vn");
        fields.put("vnp_ReturnUrl", vnpayConfig.getReturnUrl());
        fields.put("vnp_IpAddr", ipAddr);
        fields.put("vnp_CreateDate", createDate);

        // Build raw query string with url encoding
        String hashData = VnpayUtils.buildHashData(fields);
        
        // Compute secure signature using SHA-512 with HMAC Key
        String secureHash = VnpayUtils.hmacSHA512(vnpayConfig.getHashSecret(), hashData);
        
        // Build final redirect URL
        String finalUrl = vnpayConfig.getPayUrl() + "?" + hashData + "&vnp_SecureHash=" + secureHash;
        log.debug("Built VNPay payment URL for txnRef {}: {}", txnRef, finalUrl);
        return finalUrl;
    }

    /**
     * Verifies the parameters returned from VNPay (either via IPN or Return)
     * 
     * @param params All parameters received from VNPay as Map
     * @return true if signature matches
     */
    public boolean verifyCallback(Map<String, String> params) {
        String secureHash = params.get("vnp_SecureHash");
        if (secureHash == null || secureHash.isEmpty()) {
            log.warn("Missing vnp_SecureHash parameter in VNPay callback");
            return false;
        }

        SortedMap<String, String> fields = new TreeMap<>();
        params.forEach((k, v) -> {
            if (v != null && !v.isEmpty() && !k.equals("vnp_SecureHash") && !k.equals("vnp_SecureHashType")) {
                fields.put(k, v);
            }
        });

        // Rebuild hash data
        String hashData = VnpayUtils.buildHashData(fields);
        
        // Calculate signature
        String computedHash = VnpayUtils.hmacSHA512(config.getGateways().getVnpay().getHashSecret(), hashData);
        
        boolean isValid = secureHash.equalsIgnoreCase(computedHash);
        if (!isValid) {
            log.error("VNPay signature verification failed! Received: {}, Computed: {}", secureHash, computedHash);
        } else {
            log.info("VNPay signature verification successful for txnRef: {}", params.get("vnp_TxnRef"));
        }
        return isValid;
    }
}
