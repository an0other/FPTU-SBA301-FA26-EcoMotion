package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.user.enums.DriverActivityStatus;
import com.fptufa26.ecomotion.user.enums.DriverOperationalStatus;
import com.fptufa26.ecomotion.user.enums.DriverStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AvailableDriverResponse(
        Long id,
        Long userId,
        String fullName,
        String phone,
        String licenseNo,
        String licenseClass,
        LocalDate licenseExpiry,
        String portraitUrl,
        DriverStatus status,
        DriverActivityStatus activityStatus,
        DriverOperationalStatus operationalStatus,
        BigDecimal ratingAvg,
        Integer ratingCount,
        long activeBookingsCount,
        boolean eligible,
        String ineligibilityReason
) {
}
