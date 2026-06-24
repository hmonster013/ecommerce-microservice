package org.de013.paymentservice.gateway.vnpay;

import org.junit.jupiter.api.Test;
import java.util.SortedMap;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

class VnpayUtilsTest {

    @Test
    void hmacSHA512_ShouldCalculateCorrectSignature() {
        String secret = "secret_key";
        String data = "vnp_Amount=1000000&vnp_Command=pay&vnp_TxnRef=123456";
        
        String expectedHash = VnpayUtils.hmacSHA512(secret, data);
        assertNotNull(expectedHash);
        assertFalse(expectedHash.isEmpty());
        assertEquals(128, expectedHash.length()); // SHA-512 is 128 hex chars
    }

    @Test
    void urlEncode_ShouldEncodeSpacesAsPct20() {
        String input = "Hello World";
        String encoded = VnpayUtils.urlEncode(input);
        assertEquals("Hello%20World", encoded); // spaces become %20
    }

    @Test
    void buildHashData_ShouldSortAndFormatQueryString() {
        SortedMap<String, String> fields = new TreeMap<>();
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", "TMN123");

        String hashData = VnpayUtils.buildHashData(fields);
        assertEquals("vnp_Command=pay&vnp_TmnCode=TMN123&vnp_Version=2.1.0", hashData);
    }
}
