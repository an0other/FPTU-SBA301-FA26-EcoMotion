package com.fptufa26.ecomotion.vehicle.service;

import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.enums.VehicleSort;
import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleCardResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleDetailResponse;

public interface VehicleService {

    PageResponse<VehicleCardResponse> listRentableVehicles(String keyword, String location,
                                                           String fuelType, String planType,
                                                           java.math.BigDecimal maxPrice,
                                                           VehicleSort sort,
                                                           int offset, int limit);

    VehicleDetailResponse getVehicleDetail(Long id);

    String getCoverImageUrl(Long vehicleId);

    java.util.Map<Long, String> getCoverImageUrls(java.util.Collection<Long> vehicleIds);
}
