package org.de013.paymentservice.gateway.vnpay;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.SortedMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utility class for VNPay gateway signature and query building
 */
public class VnpayUtils {

    /**
     * Compute HMAC-SHA512 signature
     */
    public static String hmacSHA512(String key, String data) {
        try {
            if (key == null || data == null) {
                throw new NullPointerException("Key or data is null");
            }
            final Mac hmac512 = Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes = key.getBytes(StandardCharsets.UTF_8);
            final SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
            hmac512.init(secretKey);
            byte[] result = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new RuntimeException("Error computing HMAC-SHA512", ex);
        }
    }

    /**
     * URL encode values with UTF-8 and replace "+" with "%20" to match VNPay specification
     */
    public static String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
                    .replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    /**
     * Build the raw query string for hash calculation (must use urlEncode)
     */
    public static String buildHashData(SortedMap<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        fields.forEach((k, v) -> {
            if (v != null && !v.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('&');
                }
                sb.append(urlEncode(k));
                sb.append('=');
                sb.append(urlEncode(v));
            }
        });
        return sb.toString();
    }
}
