package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.CancelReason;

import java.math.BigDecimal;
import java.time.Instant;

public record CancelBookingResponse(
        String bookingCode,
        BookingStatus status,
        CancelReason cancelReason,
        Instant cancelledAt,
        BigDecimal refundAmount
) {
}
