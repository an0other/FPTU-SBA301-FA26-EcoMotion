package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.enums.BookingStatus;

import java.time.Instant;

public record ConfirmEndResponse(
        String bookingCode,
        BookingStatus status,
        Instant renterConfirmedAt,
        boolean completed
) {
}
