package com.fptufa26.ecomotion.vehicle.service;

import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.enums.VehicleSort;
import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleCardResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleDetailResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleMapResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleAvailabilityResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface VehicleService {

    PageResponse<VehicleCardResponse> listRentableVehicles(String keyword, String location,
                                                           String fuelType, String planType,
                                                           java.math.BigDecimal maxPrice,
                                                           VehicleSort sort,
                                                           int offset, int limit);

    VehicleDetailResponse getVehicleDetail(Long id);

    List<VehicleMapResponse> findNearbyVehicles(BigDecimal latitude, BigDecimal longitude,
                                                int radiusKm, String keyword, String location,
                                                String fuelType, String planType, BigDecimal maxPrice,
                                                VehicleSort sort);

    VehicleAvailabilityResponse getAvailability(Long vehicleId, Instant from, Instant to);
}
