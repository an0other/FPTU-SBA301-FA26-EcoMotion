package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentTransactionResponse(
        Long id,
        String transactionCode,
        Long chargeId,
        PaymentType type,
        BigDecimal amount,
        PaymentDirection direction,
        PaymentMethod method,
        PaymentStatus status,
        String gatewayRef,
        Instant paidAt,
        Instant createdAt
) {
    public PaymentTransactionResponse(
            Long id,
            Long chargeId,
            PaymentType type,
            BigDecimal amount,
            PaymentDirection direction,
            PaymentMethod method,
            PaymentStatus status,
            String gatewayRef,
            Instant paidAt,
            Instant createdAt
    ) {
        this(id, null, chargeId, type, amount, direction, method, status, gatewayRef, paidAt, createdAt);
    }
}
