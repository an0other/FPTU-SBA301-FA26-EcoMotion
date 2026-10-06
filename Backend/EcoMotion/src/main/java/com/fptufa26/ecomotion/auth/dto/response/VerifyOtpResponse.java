package com.fptufa26.ecomotion.auth.dto.response;

public record VerifyOtpResponse(
        String registrationToken,
        long expiresInSeconds
) {
}
