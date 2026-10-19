package com.fptufa26.ecomotion.vehicle.dto.response;

import java.math.BigDecimal;

public record VehicleMapResponse(
        VehicleCardResponse vehicle,
        BigDecimal depotLatitude,
        BigDecimal depotLongitude,
        double distanceKm
) {
}
