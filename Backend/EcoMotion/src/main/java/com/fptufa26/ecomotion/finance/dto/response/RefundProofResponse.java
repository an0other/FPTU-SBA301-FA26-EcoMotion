package com.fptufa26.ecomotion.finance.dto.response;

import com.fptufa26.ecomotion.booking.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record RefundProofResponse(
        Long transactionId,
        String bookingCode,
        BigDecimal amount,
        PaymentStatus status,
        String bankReference,
        String note,
        String proofUrl,
        Instant completedAt
) {
}
