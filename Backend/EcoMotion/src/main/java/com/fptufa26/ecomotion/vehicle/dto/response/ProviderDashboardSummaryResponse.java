package com.fptufa26.ecomotion.vehicle.dto.response;

public record ProviderDashboardSummaryResponse(
        String providerName,
        String depotAddress,
        String hotline,
        long totalVehicles,
        long pendingVehicles,
        long approvedVehicles,
        long hiddenVehicles,
        long totalDrivers,
        long activeBlocks
) {
}
