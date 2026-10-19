package com.fptufa26.ecomotion.vehicle.dto.response;

import java.util.List;

public record ProviderVehiclePageResponse(
        ProviderVehicleStatsResponse stats,
        List<ProviderVehicleItemResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}

