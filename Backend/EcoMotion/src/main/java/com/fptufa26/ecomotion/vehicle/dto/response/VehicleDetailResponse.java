package com.fptufa26.ecomotion.vehicle.dto.response;

import com.fptufa26.ecomotion.user.entity.ProviderType;
import com.fptufa26.ecomotion.vehicle.entity.FuelType;
import com.fptufa26.ecomotion.vehicle.entity.PlanType;
import com.fptufa26.ecomotion.vehicle.entity.Transmission;

import java.math.BigDecimal;
import java.util.List;

public record VehicleDetailResponse(
        Long id,
        String brand,
        String model,
        Integer manufactureYear,
        Integer seatCount,
        Transmission transmission,
        FuelType fuelType,
        String color,
        BigDecimal ratingAvg,
        Integer ratingCount,
        List<String> imageUrls,
        ProviderInfo provider,
        List<PlanDetail> plans
) {

    public record ProviderInfo(
            Long id,
            String displayName,
            ProviderType providerType,
            String hotline,
            String depotAddress,
            BigDecimal ratingAvg,
            Integer ratingCount
    ) {
    }

    public record PlanDetail(
            PlanType planType,
            Integer slotLengthMinutes,
            BigDecimal slotPrice,
            BigDecimal upfrontAmount,
            Integer allowedOvertimeMinutes,
            BigDecimal overtimeSurchargeRatio,
            Integer freeRadiusKm,
            BigDecimal outOfRadiusRate,
            Integer waitingFreeMinutes,
            BigDecimal waitingRate,
            BigDecimal weekendSurchargeRatio,
            Integer hourlyToDailyThresholdMinutes
    ) {
    }
}
