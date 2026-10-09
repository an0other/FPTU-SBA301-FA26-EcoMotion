package com.fptufa26.ecomotion.auth.dto.response;

public record SendPhoneOtpResponse(
        boolean sent,
        String otp,
        long expiresInSeconds,
        long resendCooldownSeconds
) {
}
