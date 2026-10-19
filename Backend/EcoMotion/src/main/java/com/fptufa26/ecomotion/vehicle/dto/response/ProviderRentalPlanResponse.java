package com.fptufa26.ecomotion.vehicle.dto.response;

import com.fptufa26.ecomotion.vehicle.enums.PlanStatus;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import java.math.BigDecimal;

public record ProviderRentalPlanResponse(
        Long id,
        PlanType planType,
        Integer slotLengthMinutes,
        BigDecimal slotPrice,
        BigDecimal upfrontAmount,
        PlanStatus status,
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

