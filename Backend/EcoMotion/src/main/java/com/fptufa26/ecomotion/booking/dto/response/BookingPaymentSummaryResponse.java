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
        // B1: khoản giảm trừ do đổi sang xe rẻ hơn (đã trừ vào tổng chi phí).
        BigDecimal vehicleChangeDiscount,
        boolean provisional,
        String balanceNote
) {
}