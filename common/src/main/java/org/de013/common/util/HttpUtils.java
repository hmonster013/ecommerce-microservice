package org.de013.common.util;

import jakarta.servlet.http.HttpServletRequest;

public final class HttpUtils {
    
    // Private constructor to prevent instantiation
    private HttpUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    private static final String[] IP_HEADER_CANDIDATES = {
        "X-Forwarded-For",
        "Proxy-Client-IP",
        "WL-Proxy-Client-IP",
        "HTTP_X_FORWARDED_FOR",
        "HTTP_X_FORWARDED",
        "HTTP_X_CLUSTER_CLIENT_IP",
        "HTTP_CLIENT_IP",
        "HTTP_FORWARDED_FOR",
        "HTTP_FORWARDED",
        "HTTP_VIA",
        "REMOTE_ADDR"
    };
    
    /**
     * Get client IP address from HTTP request
     * Handles cases where request goes through proxy/load balancer
     */
    public static String getClientIpAddress(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        
        for (String header : IP_HEADER_CANDIDATES) {
            String ipList = request.getHeader(header);
            if (ipList != null && !ipList.isEmpty() && !"unknown".equalsIgnoreCase(ipList)) {
                // X-Forwarded-For can contain multiple IPs, take the first one
                String ip = ipList.split(",")[0].trim();
                if (isValidIpAddress(ip)) {
                    return ip;
                }
            }
        }
        
        // Fallback to remote address
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "unknown";
    }
    
    /**
     * Get User-Agent from HTTP request
     */
    public static String getUserAgent(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent : "unknown";
    }
    
    /**
     * Get request URI with query string
     */
    public static String getFullRequestUri(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        
        if (queryString != null && !queryString.isEmpty()) {
            return uri + "?" + queryString;
        }
        return uri;
    }
    
    /**
     * Basic IP address validation
     */
    private static boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            return false;
        }
        
        // Check for localhost variations
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return true; // IPv6 localhost
        }
        
        // Basic IPv4 validation
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            try {
                for (String part : parts) {
                    int num = Integer.parseInt(part);
                    if (num < 0 || num > 255) {
                        return false;
                    }
                }
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        
        // For IPv6 or other formats, assume valid if not empty
        return !ip.trim().isEmpty();
    }
}
