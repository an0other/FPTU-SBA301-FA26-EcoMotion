package com.fptufa26.ecomotion.user.dto.response;

import java.time.LocalDate;

public record DrivingLicenseClassOptionResponse(
        String code,
        String label,
        LocalDate validFrom,
        boolean autoEligibleForYearlyRental,
        String note
) {
}
