package com.fptufa26.ecomotion.user.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateDrivingLicenseRequest(
        @NotBlank
        @Size(max = 20)
        String licenseNo,

        @NotBlank
        @Size(max = 10)
        String licenseClass,

        @NotNull
        @FutureOrPresent
        LocalDate licenseExpiry
) {
}