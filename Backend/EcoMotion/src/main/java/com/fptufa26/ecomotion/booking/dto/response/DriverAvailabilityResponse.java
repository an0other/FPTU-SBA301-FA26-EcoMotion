package com.fptufa26.ecomotion.booking.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DriverAvailabilityResponse(
        Long id,
        Long userId,
        String fullName,
        String phone,
        String email,
        String licenseNo,
        String licenseClass,
        LocalDate licenseExpiry,
        String portraitUrl,
        BigDecimal ratingAvg,
        Integer ratingCount,
        boolean available,
        String conflictReason
) {
}
