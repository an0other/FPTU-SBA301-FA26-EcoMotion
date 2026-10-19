package com.fptufa26.ecomotion.vehicle.dto.response;

import com.fptufa26.ecomotion.vehicle.enums.PlanType;

import java.math.BigDecimal;

public record RentalPlanResponse(
        Long id,
        PlanType planType,
        int slotLengthMinutes,
        BigDecimal slotPrice,
        BigDecimal upfrontAmount
) {
}
