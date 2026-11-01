package com.fptufa26.ecomotion.finance.dto.response;

import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PaymentRequestResponse(
        Long id,
        String paymentCode,
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
        Instant firstProofAt,
        Instant reconciliationDeadline,
        Instant createdAt,
        Instant confirmedAt,
        String rejectionReason,
        String issueNote,
        List<PaymentEvidenceResponse> evidences
) {
    public PaymentRequestResponse(
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
            Instant firstProofAt,
            Instant reconciliationDeadline,
            Instant createdAt,
            Instant confirmedAt,
            String rejectionReason,
            String issueNote,
            List<PaymentEvidenceResponse> evidences
    ) {
        this(id, null, bookingId, bookingCode, paymentType, amount, paidAmount, remainingAmount, currency,
                transferReference, bankCodeSnapshot, bankNameSnapshot, accountNumberSnapshot, accountNumberMasked,
                accountHolderSnapshot, status, qrCodeUrl, expiresAt, firstProofAt, reconciliationDeadline,
                createdAt, confirmedAt, rejectionReason, issueNote, evidences);
    }
}
