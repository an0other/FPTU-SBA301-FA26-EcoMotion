package com.fptufa26.ecomotion.finance.dto.response;

import java.math.BigDecimal;

public record ProviderFinanceSummaryResponse(
        BigDecimal totalConfirmedRevenue,
        BigDecimal pendingPayments,
        BigDecimal approvedSurcharges,
        BigDecimal refundedAmount,
        BigDecimal estimatedCommission,
        BigDecimal netEarnings,
        long totalBookingsCount,
        long pendingBookingsCount,
        long completedBookingsCount
) {
}
