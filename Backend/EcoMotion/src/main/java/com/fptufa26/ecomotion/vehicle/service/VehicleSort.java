package com.fptufa26.ecomotion.vehicle.service;

import java.util.Locale;

public enum VehicleSort {

    DEFAULT,
    PRICE_ASC,
    PRICE_DESC,
    RATING;

    public static VehicleSort from(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "price-asc" -> PRICE_ASC;
            case "price-desc" -> PRICE_DESC;
            case "rating" -> RATING;
            default -> DEFAULT;
        };
    }
}
