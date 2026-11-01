package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentHistoryResponse(
        String bookingCode,
        String transactionCode,
        PaymentType type,
        BigDecimal amount,
        PaymentDirection direction,
        PaymentMethod method,
        PaymentStatus status,
        String receiptUrl,
        Instant paidAt,
        Instant createdAt
) {
    public PaymentHistoryResponse(
            String bookingCode,
            PaymentType type,
            BigDecimal amount,
            PaymentDirection direction,
            PaymentMethod method,
            PaymentStatus status,
            String receiptUrl,
            Instant paidAt,
            Instant createdAt
    ) {
        this(bookingCode, null, type, amount, direction, method, status, receiptUrl, paidAt, createdAt);
    }
}