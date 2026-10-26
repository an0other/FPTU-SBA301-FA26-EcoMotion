package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.enums.BookingStatus;

import java.time.Instant;

public record ConfirmStartResponse(
        String bookingCode,
        BookingStatus status,
        Instant renterStartConfirmedAt,
        Instant driverStartConfirmedAt,
        Instant actualStartAt,
        boolean started
) {
}
