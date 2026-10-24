package com.fptufa26.ecomotion.vehicle.dto.response;

import com.fptufa26.ecomotion.vehicle.enums.FuelType;

import java.math.BigDecimal;
import java.util.List;

public record BookableVehicleResponse(
        Long id,
        String name,
        int manufactureYear,
        String licensePlate,
        int seatCount,
        FuelType fuelType,
        String imageUrl,
        BigDecimal depositRatio,
        List<RentalPlanResponse> plans
) {
}
