package com.fptufa26.ecomotion.finance.dto.response;

import com.fptufa26.ecomotion.finance.enums.PaymentEvidenceStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentEvidenceResponse(
        Long id,
        Long paymentRequestId,
        String proofImageUrl,
        BigDecimal transferredAmount,
        String transactionReference,
        Instant transferredAt,
        String note,
        Long submittedById,
        String submittedByName,
        PaymentEvidenceStatus status,
        Instant createdAt,
        Instant reviewedAt
) {
}
