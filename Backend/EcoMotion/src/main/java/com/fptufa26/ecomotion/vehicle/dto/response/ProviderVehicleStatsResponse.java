package com.fptufa26.ecomotion.vehicle.dto.response;

public record ProviderVehicleStatsResponse(
        long total,
        long approved,
        long pending,
        long hidden,
        long rejected
) {
}

