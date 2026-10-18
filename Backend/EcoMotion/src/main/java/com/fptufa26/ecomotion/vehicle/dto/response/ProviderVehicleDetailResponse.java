package com.fptufa26.ecomotion.vehicle.dto.response;

import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.Transmission;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
        Instant updatedAt,
        List<ProviderRentalPlanResponse> plans
) {

    public ProviderVehicleDetailResponse(
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
        this(
                id,
                licensePlate,
                chassisNo,
                engineNo,
                brand,
                model,
                manufactureYear,
                seatCount,
                transmission,
                fuelType,
                color,
                status,
                maintenanceBufferMinutes,
                coverImageUrl,
                ratingAvg,
                ratingCount,
                createdAt,
                updatedAt,
                List.of()
        );
    }
}
