package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.entity.ChargeType;

import java.math.BigDecimal;

public record BookingChargeResponse(
        ChargeType chargeType,
        String description,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal amount
) {
}
