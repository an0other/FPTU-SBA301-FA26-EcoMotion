package com.fptufa26.ecomotion.vehicle.dto.response;

import java.math.BigDecimal;

public record VehicleDepositPolicyResponse(
        Long vehicleId,
        BigDecimal providerDefaultRatio,
        BigDecimal depositRatioOverride,
        BigDecimal minimumDepositAmount,
        BigDecimal effectiveRatio,
        BigDecimal exampleRentalAmount,
        BigDecimal exampleDepositAmount
) {
}
