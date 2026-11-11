package com.fptufa26.ecomotion.finance.dto.response;

import com.fptufa26.ecomotion.finance.enums.CommissionStatementStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CommissionStatementResponse(
        Long id,
        Long providerId,
        String providerName,
        String periodMonth,
        Instant periodStart,
        Instant periodEnd,
        Instant dueAt,
        BigDecimal grossRevenue,
        BigDecimal commissionRate,
        BigDecimal commissionAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        int bookingCount,
        CommissionStatementStatus status,
        long overdueDays,
        boolean blocking,
        Instant reportedAt,
        String paymentReference,
        String reportedNote,
        Instant confirmedAt,
        String adminNote,
        String transferContent,
        String qrCodeUrl,
        List<Line> lines
) {
    public record Line(Long bookingId, String bookingCode, Instant completedAt, BigDecimal revenue, BigDecimal commission) {
    }
}
