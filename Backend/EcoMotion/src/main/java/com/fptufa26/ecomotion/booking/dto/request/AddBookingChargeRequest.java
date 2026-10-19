package com.fptufa26.ecomotion.booking.dto.request;

import com.fptufa26.ecomotion.booking.enums.ChargeType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AddBookingChargeRequest(
        @NotNull(message = "chargeType is required")
        ChargeType chargeType,

        @NotBlank(message = "description is required")
        @Size(max = 500, message = "description cannot exceed 500 characters")
        String description,

        Integer quantity,

        @DecimalMin(value = "0.0", inclusive = true, message = "unitPrice cannot be negative")
        BigDecimal unitPrice,

        BigDecimal appliedRate,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        BigDecimal amount,

        @Size(max = 500, message = "evidenceUrl cannot exceed 500 characters")
        String evidenceUrl
) {
}
