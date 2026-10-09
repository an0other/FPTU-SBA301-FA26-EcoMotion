package com.fptufa26.ecomotion.auth.dto.response;

public record ScanCccdResponse(
        boolean scanned,
        String fullName,
        String cccdNo,
        String dateOfBirth,
        String gender,
        String address,
        String cccdFrontUrl,
        String message
) {
}
