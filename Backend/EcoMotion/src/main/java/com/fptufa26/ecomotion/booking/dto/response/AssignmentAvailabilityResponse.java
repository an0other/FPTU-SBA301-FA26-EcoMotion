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
        Instant lastUpdatedAt,
        String currentOperationalStatus
) {
    public AssignmentAvailabilityResponse(
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
        this(workStatus, availabilityStatus, reason, currentBookingCode, currentTripStartedAt,
                conflictingBookingCode, conflictStart, conflictEnd, nextBookingCode, nextPickupAt,
                nextAvailableAt, lastUpdatedAt, deriveOperationalStatus(workStatus, currentBookingCode, nextBookingCode));
    }

    private static String deriveOperationalStatus(String workStatus, String currentBookingCode, String nextBookingCode) {
        if ("OFFLINE".equalsIgnoreCase(workStatus)) return "OFFLINE";
        if ("UNAVAILABLE".equalsIgnoreCase(workStatus)) return "UNAVAILABLE";
        if (currentBookingCode != null && !currentBookingCode.isBlank()) return "ON_TRIP";
        if (nextBookingCode != null && !nextBookingCode.isBlank()) return "SCHEDULED";
        return "AVAILABLE";
    }
}
