package org.de013.paymentservice.gateway.vnpay;

import org.de013.paymentservice.config.PaymentGatewayConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VnpayPaymentGatewayTest {

    @Mock
    private PaymentGatewayConfig config;

    private VnpayPaymentGateway vnpayPaymentGateway;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        PaymentGatewayConfig.Gateways gateways = new PaymentGatewayConfig.Gateways();
        PaymentGatewayConfig.Vnpay vnpay = new PaymentGatewayConfig.Vnpay();
        vnpay.setEnabled(true);
        vnpay.setTmnCode("TMN12345");
        vnpay.setHashSecret("ABCDEF");
        vnpay.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        vnpay.setReturnUrl("http://localhost:8080/webhooks/vnpay/return");
        vnpay.setVersion("2.1.0");
        vnpay.setVndRate(25000);
        
        gateways.setVnpay(vnpay);
        when(config.getGateways()).thenReturn(gateways);

        vnpayPaymentGateway = new VnpayPaymentGateway(config);
    }

    @Test
    void buildPaymentUrl_ShouldConstructValidVnpayUrl() {
        String url = vnpayPaymentGateway.buildPaymentUrl(
                "PMT-999",
                1000000,
                "Payment test",
                "127.0.0.1",
                "20260624200000"
        );

        assertNotNull(url);
        assertTrue(url.startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html"));
        assertTrue(url.contains("vnp_TxnRef=PMT-999"));
        assertTrue(url.contains("vnp_Amount=100000000")); // 1M * 100
        assertTrue(url.contains("vnp_SecureHash="));
    }

    @Test
    void verifyCallback_WhenSignatureIsValid_ShouldReturnTrue() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TxnRef", "PMT-999");
        params.put("vnp_ResponseCode", "00");

        // Calculate expected hash
        String hashData = VnpayUtils.buildHashData(new TreeMap<>(params));
        String secureHash = VnpayUtils.hmacSHA512("ABCDEF", hashData);
        params.put("vnp_SecureHash", secureHash);

        assertTrue(vnpayPaymentGateway.verifyCallback(params));
    }

    @Test
    void verifyCallback_WhenSignatureIsInvalid_ShouldReturnFalse() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_TxnRef", "PMT-999");
        params.put("vnp_SecureHash", "invalid_signature");

        assertFalse(vnpayPaymentGateway.verifyCallback(params));
    }
}
