package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.enums.ChargeConfirmationStatus;
import com.fptufa26.ecomotion.booking.enums.ChargeType;

import java.math.BigDecimal;

public record BookingChargeLineResponse(
        Long chargeId,
        ChargeType chargeType,
        String description,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal appliedRate,
        BigDecimal amount,
        boolean isWaived,
        BigDecimal effectiveAmount,
        ChargeConfirmationStatus confirmationStatus,
        String evidenceUrl,
        String waivedSource
) {
    public BookingChargeLineResponse(Long chargeId, ChargeType chargeType, String description, Integer quantity,
                                     BigDecimal unitPrice, BigDecimal appliedRate, BigDecimal amount, boolean isWaived,
                                     BigDecimal effectiveAmount, ChargeConfirmationStatus confirmationStatus, String evidenceUrl) {
        this(chargeId, chargeType, description, quantity, unitPrice, appliedRate, amount, isWaived, effectiveAmount,
                confirmationStatus, evidenceUrl, null);
    }
}
