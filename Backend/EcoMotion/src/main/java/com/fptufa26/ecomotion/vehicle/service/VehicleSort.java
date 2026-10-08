package com.fptufa26.ecomotion.vehicle.service;

import java.util.Locale;

/**
 * Ordering of the rentable fleet.
 *
 * <p>Ordering is applied server-side, before the offset/limit slice. It used to run in the browser
 * over the page already on screen, which only ever reordered those six cards: "price low to high"
 * left the rest of the fleet unsorted and page 2 was unrelated to page 1.
 *
 * <p>An enum rather than a {@code String} because {@link VehicleService#listRentableVehicles} is
 * already a run of adjacent nullable strings (keyword, location, fuelType, planType); a sixth one
 * could be swapped with its neighbour and still compile.
 */
public enum VehicleSort {

    /** Fleet order - the repository's id order, which is what the list opens on. */
    DEFAULT,
    PRICE_ASC,
    PRICE_DESC,
    RATING;

    /**
     * Maps the wire value used by the client ({@code price-asc}) onto the enum.
     *
     * <p>Anything unrecognised falls back to {@link #DEFAULT} instead of failing the request: a bad
     * sort is not worth a 400 when the list can simply come back in its natural order.
     */
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
