package com.fptufa26.ecomotion.user.dto.response;

import java.time.LocalDate;

public record DrivingLicenseResponse(
        String licenseNo,
        String licenseClass,
        LocalDate licenseExpiry,
        boolean licenseVerified
) {
}