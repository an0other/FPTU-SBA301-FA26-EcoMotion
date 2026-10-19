package com.fptufa26.ecomotion.vehicle.dto;

import com.fptufa26.ecomotion.vehicle.enums.PlanType;

import java.math.BigDecimal;

public record RentalPlanData(
        Long id,
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
    public RentalPlanData(PlanType planType, Integer slotLengthMinutes, BigDecimal slotPrice,
                          BigDecimal upfrontAmount, Integer allowedOvertimeMinutes,
                          BigDecimal overtimeSurchargeRatio, Integer freeRadiusKm,
                          BigDecimal outOfRadiusRate, Integer waitingFreeMinutes,
                          BigDecimal waitingRate, BigDecimal weekendSurchargeRatio,
                          Integer hourlyToDailyThresholdMinutes) {
        this(null, planType, slotLengthMinutes, slotPrice, upfrontAmount, allowedOvertimeMinutes,
                overtimeSurchargeRatio, freeRadiusKm, outOfRadiusRate, waitingFreeMinutes,
                waitingRate, weekendSurchargeRatio, hourlyToDailyThresholdMinutes);
    }
}
