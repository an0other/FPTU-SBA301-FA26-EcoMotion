package com.fptufa26.ecomotion.finance.config;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class VnpayUtils {

    private static final String HMAC_SHA512 = "HmacSHA512";

    private VnpayUtils() {
    }

    public static String hmacSHA512(String key, String data) {
        try {
            if (key == null || data == null) {
                throw new IllegalArgumentException("Key and data cannot be null");
            }
            Mac hmac512 = Mac.getInstance(HMAC_SHA512);
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA512);
            hm512Init(hmac512, secretKey);
            byte[] result = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute HMAC SHA512", ex);
        }
    }

    private static void hm512Init(Mac mac, SecretKeySpec keySpec) {
        try {
            mac.init(keySpec);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to init HMAC", ex);
        }
    }

    public static String buildHashData(Map<String, String> fields) {
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);
        StringBuilder sb = new StringBuilder();
        for (String fieldName : fieldNames) {
            String fieldValue = fields.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append('&');
                }
                sb.append(fieldName);
                sb.append('=');
                sb.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
            }
        }
        return sb.toString();
    }

    public static String buildQueryUrl(Map<String, String> fields) {
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);
        StringBuilder sb = new StringBuilder();
        for (String fieldName : fieldNames) {
            String fieldValue = fields.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append('&');
                }
                sb.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII));
                sb.append('=');
                sb.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
            }
        }
        return sb.toString();
    }
}
