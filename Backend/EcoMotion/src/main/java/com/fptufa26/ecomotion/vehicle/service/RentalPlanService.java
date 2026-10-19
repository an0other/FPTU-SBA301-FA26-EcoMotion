package com.fptufa26.ecomotion.vehicle.service;

import com.fptufa26.ecomotion.vehicle.dto.RentalPlanData;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface RentalPlanService {

    List<RentalPlanData> findActivePlans(Long vehicleId);

    Map<Long, List<RentalPlanData>> findActivePlansByVehicleIds(Collection<Long> vehicleIds);
}
