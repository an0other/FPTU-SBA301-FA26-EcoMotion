package com.fptufa26.ecomotion.vehicle.dto.response;

import java.time.Instant;

public record VehicleLicensePlateHistoryResponse(
        Long id,
        String previousPlate,
        String newPlate,
        String changedBy,
        Instant changedAt
) {
}
