package com.fptufa26.ecomotion.finance.dto.response;

import com.fptufa26.ecomotion.finance.enums.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record RefundResponse(
        Long id,
        Long bookingId,
        String bookingCode,
        Long paymentTransactionId,
        BigDecimal amount,
        String reason,
        String evidenceUrl,
        RefundStatus status,
        Long requestedById,
        String requestedByName,
        Long approvedById,
        String approvedByName,
        String rejectionReason,
        String gatewayRefundRef,
        Instant createdAt,
        Instant updatedAt
) {
}
