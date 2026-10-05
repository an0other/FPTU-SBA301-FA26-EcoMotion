package com.fptufa26.ecomotion.vehicle.service;

import com.fptufa26.ecomotion.vehicle.dto.response.BookableVehicleResponse;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;

import java.util.List;

public interface VehicleCatalogService {

    List<BookableVehicleResponse> listApproved();

    Vehicle requireApproved(Long vehicleId);

    RentalPlan requireActivePlan(Long vehicleId, Long planId);
}
