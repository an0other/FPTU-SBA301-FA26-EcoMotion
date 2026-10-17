package com.fptufa26.ecomotion.vehicle.service;

import com.fptufa26.ecomotion.vehicle.dto.request.VehicleVisibilityRequest;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderDashboardSummaryResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehicleItemResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehiclePageResponse;
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;

public interface ProviderVehicleService {

    ProviderDashboardSummaryResponse getDashboardSummary(Long userId);

    ProviderVehiclePageResponse getProviderVehicles(
            Long userId,
            String keyword,
            FuelType fuelType,
            VehicleStatus status,
            String sortBy,
            int page,
            int size
    );

    ProviderVehicleItemResponse toggleVehicleVisibility(
            Long userId,
            Long vehicleId,
            VehicleVisibilityRequest request
    );

    com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehicleDetailResponse createVehicle(
            Long userId,
            com.fptufa26.ecomotion.vehicle.dto.request.CreateVehicleRequest request
    );
}
