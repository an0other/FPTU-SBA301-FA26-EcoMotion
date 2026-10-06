package com.fptufa26.ecomotion.auth.dto.response;

/**
 * Result of scanning the two CCCD sides: the identity fields read from the front image, plus the
 * stored paths of both sides. The client uses these fields to auto-fill the editable profile form.
 */
public record ScanCccdResponse(
        boolean scanned,
        String fullName,
        String cccdNo,
        String dateOfBirth,
        String gender,
        String address,
        String cccdFrontUrl,
        String cccdBackUrl,
        String message
) {
}
