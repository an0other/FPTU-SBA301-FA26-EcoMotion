package com.fptufa26.ecomotion.user.dto.response;

import com.fptufa26.ecomotion.user.enums.DriverActivityStatus;
import com.fptufa26.ecomotion.user.enums.DriverOperationalStatus;

import java.time.Instant;

public record DriverStatusResponse(
        Long driverId,
        Long userId,
        String fullName,
        DriverActivityStatus activityStatus,
        DriverOperationalStatus operationalStatus,
        Instant statusUpdatedAt,
        DriverTripSummaryResponse activeTrip,
        DriverTripSummaryResponse nextTrip
) {
}
