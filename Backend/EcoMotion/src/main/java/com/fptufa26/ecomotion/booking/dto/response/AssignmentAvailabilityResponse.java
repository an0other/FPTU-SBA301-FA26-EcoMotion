package com.fptufa26.ecomotion.booking.dto.response;

import java.time.Instant;

public record AssignmentAvailabilityResponse(
        String workStatus,
        String availabilityStatus,
        String reason,
        String currentBookingCode,
        Instant currentTripStartedAt,
        String conflictingBookingCode,
        Instant conflictStart,
        Instant conflictEnd,
        String nextBookingCode,
        Instant nextPickupAt,
        Instant nextAvailableAt,
        Instant lastUpdatedAt
) {
}
