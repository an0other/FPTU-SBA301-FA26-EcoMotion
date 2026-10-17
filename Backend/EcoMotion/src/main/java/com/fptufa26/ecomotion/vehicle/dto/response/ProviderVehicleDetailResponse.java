package com.fptufa26.ecomotion.vehicle.dto.response;

import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.Transmission;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record ProviderVehicleDetailResponse(
        Long id,
        String licensePlate,
        String chassisNo,
        String engineNo,
        String brand,
        String model,
        Integer manufactureYear,
        Integer seatCount,
        Transmission transmission,
        FuelType fuelType,
        String color,
        VehicleStatus status,
        Integer maintenanceBufferMinutes,
        String coverImageUrl,
        BigDecimal ratingAvg,
        Integer ratingCount,
        Instant createdAt,
        Instant updatedAt
) {
}
