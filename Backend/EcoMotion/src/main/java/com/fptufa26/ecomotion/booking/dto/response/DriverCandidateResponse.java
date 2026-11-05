package com.fptufa26.ecomotion.booking.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DriverCandidateResponse(
        Long driverId,
        @com.fasterxml.jackson.annotation.JsonIgnore Long userId,
        String fullName,
        @com.fasterxml.jackson.annotation.JsonIgnore String phone,
        @com.fasterxml.jackson.annotation.JsonIgnore String licenseNo,
        @com.fasterxml.jackson.annotation.JsonIgnore String licenseClass,
        @com.fasterxml.jackson.annotation.JsonIgnore LocalDate licenseExpiry,
        String portraitUrl,
        BigDecimal ratingAvg,
        Integer ratingCount,
        Integer etaMinutes,
        Double distanceKm,
        Long activeTripsCount,
        Double suitabilityScore,
        boolean eligible,
        @com.fasterxml.jackson.annotation.JsonIgnore String ineligibilityReason,
        String recommendationReason,
        boolean isBestMatch,
        AssignmentAvailabilityResponse availability
) {
    public DriverCandidateResponse(Long driverId, Long userId, String fullName, String phone,
            String licenseNo, String licenseClass, LocalDate licenseExpiry, String portraitUrl,
            BigDecimal ratingAvg, Integer ratingCount, Integer etaMinutes, Double distanceKm,
            Long activeTripsCount, Double suitabilityScore, boolean eligible, String ineligibilityReason,
            String recommendationReason, boolean isBestMatch) {
        this(driverId, userId, fullName, phone, licenseNo, licenseClass, licenseExpiry, portraitUrl,
                ratingAvg, ratingCount, etaMinutes, distanceKm, activeTripsCount, suitabilityScore,
                eligible, ineligibilityReason, recommendationReason, isBestMatch, null);
    }

    @Override
    public String toString() {
        return "DriverCandidateResponse[driverId=" + driverId + ", eligible=" + eligible + "]";
    }

    public DriverCandidateResponse withAvailability(AssignmentAvailabilityResponse state) {
        if (!eligible && state.reason() == null) {
            state = new AssignmentAvailabilityResponse(state.workStatus(), "UNAVAILABLE", "INELIGIBLE",
                    state.currentBookingCode(), state.currentTripStartedAt(), state.conflictingBookingCode(),
                    state.conflictStart(), state.conflictEnd(), state.nextBookingCode(), state.nextPickupAt(),
                    state.nextAvailableAt(), state.lastUpdatedAt(), state.currentOperationalStatus());
        }
        return new DriverCandidateResponse(driverId, userId, fullName, phone, licenseNo, licenseClass,
                licenseExpiry, portraitUrl, ratingAvg, ratingCount, etaMinutes, distanceKm,
                activeTripsCount, suitabilityScore, eligible, ineligibilityReason,
                recommendationReason, isBestMatch, state);
    }
}
