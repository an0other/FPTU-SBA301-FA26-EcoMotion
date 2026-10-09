package com.fptufa26.ecomotion.vehicle.service;

import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleCardResponse;

public interface VehicleService {

    PageResponse<VehicleCardResponse> listRentableVehicles(String keyword, String location,
                                                           String fuelType, String planType,
                                                           java.math.BigDecimal maxPrice,
                                                           VehicleSort sort,
                                                           int offset, int limit);
}
