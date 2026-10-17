package com.fptufa26.ecomotion.vehicle.service;

import com.fptufa26.ecomotion.vehicle.dto.response.ProviderDashboardSummaryResponse;

public interface ProviderVehicleService {

    ProviderDashboardSummaryResponse getDashboardSummary(Long userId);
}
