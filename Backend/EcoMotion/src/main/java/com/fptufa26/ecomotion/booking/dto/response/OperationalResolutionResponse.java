package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.OperationalResolutionType;

import java.math.BigDecimal;
import java.time.Instant;

public record OperationalResolutionResponse(
        String bookingCode,
        BookingStatus status,
        OperationalResolutionType resolution,
        Long vehicleId,
        Instant pickupTime,
        BigDecimal refundAmount,
        String message
) {
}
