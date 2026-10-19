package com.fptufa26.ecomotion.finance.dto.response;

import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PaymentRequestResponse(
        Long id,
        Long bookingId,
        String bookingCode,
        PaymentRequestType paymentType,
        BigDecimal amount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        String currency,
        String transferReference,
        String bankCodeSnapshot,
        String bankNameSnapshot,
        String accountNumberSnapshot,
        String accountNumberMasked,
        String accountHolderSnapshot,
        PaymentRequestStatus status,
        String qrCodeUrl,
        Instant expiresAt,
        Instant createdAt,
        Instant confirmedAt,
        String rejectionReason,
        String issueNote,
        List<PaymentEvidenceResponse> evidences
) {
}
