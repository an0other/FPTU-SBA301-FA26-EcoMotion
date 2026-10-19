package com.fptufa26.ecomotion.booking.dto.response;

import java.math.BigDecimal;

public record BookingPaymentSummaryResponse(
        BigDecimal recordedCost,
        BigDecimal depositRequired,
        BigDecimal upfrontRequired,
        BigDecimal paidAmount,
        BigDecimal refundedAmount,
        BigDecimal netPaidAmount,
        BigDecimal remainingAmount,
        BigDecimal creditAmount,
        boolean provisional,
        String balanceNote
) {
}