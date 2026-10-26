package com.fptufa26.ecomotion.vehicle.service;

import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleLicensePlateHistoryResponse;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;

import java.util.List;

public interface LicensePlateHistoryService {
    void recordChange(Vehicle vehicle, String previousPlate, String newPlate, User changedBy);
    List<VehicleLicensePlateHistoryResponse> listForProvider(Long userId, Long vehicleId);
}
