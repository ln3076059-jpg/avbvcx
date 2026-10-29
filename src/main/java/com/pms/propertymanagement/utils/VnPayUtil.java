package com.pms.propertymanagement.utils;

import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class VnPayUtil {

    private static final String DEFAULT_SANDBOX_SECRET = "RAOEXHYVSDDIIENYWSLDIIZTANXUXZFJ";

    private VnPayUtil() {}

    public static String hmacSHA512(String secretKey, String data) {
        try {
            String effectiveKey = (secretKey == null || secretKey.isBlank()) ? DEFAULT_SANDBOX_SECRET : secretKey;
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec keySpec = new SecretKeySpec(effectiveKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(keySpec);
            byte[] bytes = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign VNPay request", e);
        }
    }

    public static String buildQueryString(Map<String, String> params, boolean forHash) {
        List<String> fieldNames = new ArrayList<>(params.keySet());
        Collections.sort(fieldNames);

        StringBuilder sb = new StringBuilder();
        for (String key : fieldNames) {
            String value = params.get(key);
            if (value == null || value.isBlank()) continue;

            String encodedValue = urlEncode(value);

            if (sb.length() > 0) sb.append("&");
            sb.append(key).append("=").append(encodedValue);
        }
        return sb.toString();
    }

    public static String urlEncode(String input) {
        return URLEncoder.encode(input, StandardCharsets.UTF_8);
    }

    public static boolean verifySecureHash(Map<String, String> allParams, String hashSecret) {
        String receivedHash = allParams.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank()) return false;

        Map<String, String> filtered = new HashMap<>(allParams);
        filtered.remove("vnp_SecureHash");
        filtered.remove("vnp_SecureHashType");

        String hashData = buildQueryString(filtered, true);
        String calculated = hmacSHA512(hashSecret, hashData);

        return calculated.equalsIgnoreCase(receivedHash);
    }

    public static Map<String, String> extractRequestParams(Map<String, String[]> parameterMap) {
        Map<String, String> params = new HashMap<>();
        for (Map.Entry<String, String[]> e : parameterMap.entrySet()) {
            String key = e.getKey();
            String[] values = e.getValue();
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        }
        return params;
    }

    public static String getIpAddress(HttpServletRequest request) {
        String ipAddress;
        try {
            ipAddress = request.getHeader("X-FORWARDED-FOR");
            if (ipAddress == null || ipAddress.isBlank() || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getHeader("Proxy-Client-IP");
            }
            if (ipAddress == null || ipAddress.isBlank() || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getHeader("WL-Proxy-Client-IP");
            }
            if (ipAddress == null || ipAddress.isBlank() || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getRemoteAddr();
            }
            if (ipAddress != null && ipAddress.contains(",")) {
                ipAddress = ipAddress.split(",")[0].trim();
            }
        } catch (Exception e) {
            ipAddress = "127.0.0.1";
        }
        return (ipAddress != null && !ipAddress.isBlank()) ? ipAddress : "127.0.0.1";
    }

    public static String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();

        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);
        if ((scheme.equals("http") && serverPort != 80) || (scheme.equals("https") && serverPort != 443)) {
            url.append(":").append(serverPort);
        }
        url.append(request.getContextPath());
        return url.toString();
    }
}
