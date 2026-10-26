package com.fptufa26.ecomotion.vehicle.service;

import java.util.Locale;

public final class LicensePlatePolicy {

    private LicensePlatePolicy() {
    }

    public static String canonical(String value) {
        if (value == null) {
            return "";
        }
        return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    public static boolean isValid(String value) {
        return canonical(value).matches("^[0-9]{2}[A-Z]{1,2}[0-9]{4,5}$");
    }

    public static String display(String value) {
        String canonical = canonical(value);
        if (!isValid(canonical)) {
            throw new IllegalArgumentException("Biển số xe không đúng định dạng quy chuẩn Việt Nam");
        }
        int prefixLength = Character.isLetter(canonical.charAt(3)) ? 4 : 3;
        return canonical.substring(0, prefixLength) + "-" + canonical.substring(prefixLength);
    }
}
