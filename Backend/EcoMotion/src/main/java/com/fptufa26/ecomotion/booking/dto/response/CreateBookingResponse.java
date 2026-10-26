package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateBookingResponse(
        String bookingCode,
        BookingStatus status,
        BigDecimal totalAmount,
        BigDecimal depositAmount,
        Instant pickupTime,
        Instant endTime,
        BookingChargeResponse baseRental
) {
}
