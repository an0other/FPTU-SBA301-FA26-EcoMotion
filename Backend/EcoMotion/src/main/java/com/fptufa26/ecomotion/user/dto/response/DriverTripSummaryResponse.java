package com.fptufa26.ecomotion.user.dto.response;

import java.time.Instant;

public record DriverTripSummaryResponse(
        String bookingCode,
        String vehicleName,
        String pickupAddress,
        Instant pickupTime,
        String status
) {
}
