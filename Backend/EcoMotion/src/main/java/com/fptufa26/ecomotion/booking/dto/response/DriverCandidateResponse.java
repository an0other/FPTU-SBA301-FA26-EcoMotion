package com.fptufa26.ecomotion.booking.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DriverCandidateResponse(
        Long driverId,
        Long userId,
        String fullName,
        String phone,
        String licenseNo,
        String licenseClass,
        LocalDate licenseExpiry,
        String portraitUrl,
        BigDecimal ratingAvg,
        Integer ratingCount,
        Integer etaMinutes,
        Double distanceKm,
        Long activeTripsCount,
        Double suitabilityScore,
        boolean eligible,
        String ineligibilityReason,
        String recommendationReason,
        boolean isBestMatch
) {
}
