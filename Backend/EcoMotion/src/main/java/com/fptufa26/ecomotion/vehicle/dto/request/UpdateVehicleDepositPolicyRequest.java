package com.fptufa26.ecomotion.vehicle.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record UpdateVehicleDepositPolicyRequest(
        @DecimalMin(value = "0.01", message = "Tỷ lệ cọc riêng phải từ 1%")
        @DecimalMax(value = "1.00", message = "Tỷ lệ cọc riêng không được vượt quá 100%")
        BigDecimal depositRatioOverride,

        @DecimalMin(value = "0.00", message = "Mức cọc tối thiểu không được âm")
        BigDecimal minimumDepositAmount
) {
}
