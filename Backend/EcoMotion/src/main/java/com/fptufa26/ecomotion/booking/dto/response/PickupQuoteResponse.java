package com.fptufa26.ecomotion.booking.dto.response;

import java.math.BigDecimal;

public record PickupQuoteResponse(
        double distanceKm,
        int freeRadiusKm,
        int chargeableKm,
        BigDecimal outOfRadiusRate,
        BigDecimal pickupFee
) {
}
