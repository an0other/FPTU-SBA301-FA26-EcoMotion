package com.fptufa26.ecomotion.user.dto.response;

import com.fptufa26.ecomotion.user.enums.DriverActivityStatus;
import com.fptufa26.ecomotion.user.enums.DriverOperationalStatus;
import com.fptufa26.ecomotion.user.enums.DriverStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ProviderDriverItemResponse(
        Long id,
        Long userId,
        String fullName,
        String email,
        String phone,
        String avatarUrl,
        String licenseNo,
        String licenseClass,
        LocalDate licenseExpiry,
        String portraitUrl,
        String licenseImageUrl,
        DriverStatus status,
        DriverActivityStatus activityStatus,
        DriverOperationalStatus operationalStatus,
        Instant statusUpdatedAt,
        String currentTripBookingCode,
        String nextTripBookingCode,
        Instant nextTripPickupTime,
        String rejectReason,
        BigDecimal ratingAvg,
        Integer ratingCount,
        Instant createdAt,
        Instant updatedAt
) {
}
