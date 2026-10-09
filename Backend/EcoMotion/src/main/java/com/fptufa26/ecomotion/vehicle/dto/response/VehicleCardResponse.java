package com.fptufa26.ecomotion.vehicle.dto.response;

import com.fptufa26.ecomotion.vehicle.entity.FuelType;
import com.fptufa26.ecomotion.vehicle.entity.PlanType;
import com.fptufa26.ecomotion.vehicle.entity.Transmission;

import java.math.BigDecimal;
import java.util.List;

public record VehicleCardResponse(
        Long id,
        String brand,
        String model,
        Integer manufactureYear,
        String licensePlate,
        Integer seatCount,
        Transmission transmission,
        FuelType fuelType,
        String color,
        BigDecimal ratingAvg,
        Integer ratingCount,
        String coverImageUrl,
        List<String> imageUrls,
        Long providerId,
        String providerName,
        String providerHotline,
        String providerDepotAddress,
        List<PlanPrice> plans
) {

    public record PlanPrice(
            PlanType planType,
            Integer slotLengthMinutes,
            BigDecimal slotPrice,
            BigDecimal upfrontAmount
    ) {
    }
}
