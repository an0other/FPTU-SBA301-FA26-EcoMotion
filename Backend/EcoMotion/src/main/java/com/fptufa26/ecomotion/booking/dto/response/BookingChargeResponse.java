package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.enums.ChargeConfirmationStatus;
import com.fptufa26.ecomotion.booking.enums.ChargeType;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingChargeResponse(
        Long id,
        ChargeType chargeType,
        String description,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal appliedRate,
        BigDecimal amount,
        boolean waived,
        String evidenceUrl,
        String createdByName,
        Instant createdAt,
        ChargeConfirmationStatus confirmationStatus,
        String waivedSource
) {
    public BookingChargeResponse(Long id, ChargeType chargeType, String description, Integer quantity,
                                 BigDecimal unitPrice, BigDecimal appliedRate, BigDecimal amount, boolean waived,
                                 String evidenceUrl, String createdByName, Instant createdAt,
                                 ChargeConfirmationStatus confirmationStatus) {
        this(id, chargeType, description, quantity, unitPrice, appliedRate, amount, waived, evidenceUrl,
                createdByName, createdAt, confirmationStatus, null);
    }

    public BookingChargeResponse(Long id, ChargeType chargeType, String description, Integer quantity,
                                 BigDecimal unitPrice, BigDecimal appliedRate, BigDecimal amount, boolean waived,
                                 String evidenceUrl, String createdByName, Instant createdAt) {
        this(id, chargeType, description, quantity, unitPrice, appliedRate, amount, waived, evidenceUrl,
                createdByName, createdAt, ChargeConfirmationStatus.ACCEPTED);
    }

    public BookingChargeResponse(ChargeType chargeType, String description, Integer quantity,
                                 BigDecimal unitPrice, BigDecimal amount) {
        this(null, chargeType, description, quantity, unitPrice, null, amount,
                false, null, null, null, ChargeConfirmationStatus.ACCEPTED);
    }
}
