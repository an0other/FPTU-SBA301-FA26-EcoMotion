package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.enums.BookingStatus;

import java.math.BigDecimal;

public record SettlementResultResponse(
        String bookingCode,
        BookingStatus status,
        Long paymentRequestId,
        BigDecimal remainingAmount
) {
}
