package com.fptufa26.ecomotion.vehicle.dto.response;

import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.Transmission;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record ProviderVehicleItemResponse(
        Long id,
        String licensePlate,
        String brand,
        String model,
        Integer manufactureYear,
        Integer seatCount,
        Transmission transmission,
        FuelType fuelType,
        String color,
        VehicleStatus status,
        BigDecimal ratingAvg,
        Integer ratingCount,
        String coverImageUrl,
        BigDecimal dailyPrice,
        BigDecimal hourlyPrice,
        Instant createdAt
) {
}
