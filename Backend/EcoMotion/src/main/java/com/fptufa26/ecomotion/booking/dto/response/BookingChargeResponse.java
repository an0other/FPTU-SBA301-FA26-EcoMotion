package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.enums.ChargeType;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingChargeResponse(
        ChargeType chargeType,
        String description,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal appliedRate,
        BigDecimal amount,
        boolean waived,
        String evidenceUrl,
        Instant createdAt
) {
}