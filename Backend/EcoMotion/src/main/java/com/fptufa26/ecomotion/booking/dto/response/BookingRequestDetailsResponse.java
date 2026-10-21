package com.fptufa26.ecomotion.booking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingRequestDetailsResponse(
        String bookingCode,
        Instant pickupTime,
        Instant endTime,
        String pickupAddress,
        BigDecimal pickupLatitude,
        BigDecimal pickupLongitude,
        Integer passengerCount,
        String driverNote,
        boolean canCancel,
        boolean canEditRequest,
        boolean canPay
) {
}
