package com.fptufa26.ecomotion.vehicle.service;

import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.entity.VehicleDocument;
import com.fptufa26.ecomotion.vehicle.enums.VehicleSort;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleCardResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleDetailResponse;

import java.math.BigDecimal;
import java.util.List;

public interface VehicleService {

    PageResponse<VehicleCardResponse> listRentableVehicles(String keyword, String location,
                                                           String fuelType, String planType,
                                                           BigDecimal maxPrice,
                                                           VehicleSort sort,
                                                           int offset, int limit);

    VehicleDetailResponse getVehicleDetail(Long id);

    List<Vehicle> listAdminVehicles(VehicleStatus status, String keyword);

    Vehicle getAdminVehicle(Long id);

    List<VehicleDocument> getVehicleDocuments(Long vehicleId);

    Vehicle updateVehicleApproval(Long vehicleId, VehicleStatus status, String reason);

    long countVehiclesByStatus(VehicleStatus status);

    long countTotalVehicles();
}
