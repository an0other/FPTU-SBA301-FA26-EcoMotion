package com.fptufa26.ecomotion.user.dto.response;

import java.time.LocalDate;

public record DrivingLicenseResponse(
        String licenseNo,
        String licenseFullName,
        LocalDate licenseDateOfBirth,
        String licenseClass,
        LocalDate licenseIssueDate,
        LocalDate licenseExpiry,
        boolean licenseExpiryUnlimited,
        boolean licenseVerified,
        boolean yearlyBookingEligible,
        String yearlyBookingIneligibleReason,
        boolean frontImageAvailable,
        boolean backImageAvailable
) {
}