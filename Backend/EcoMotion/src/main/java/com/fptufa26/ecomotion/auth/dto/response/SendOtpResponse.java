package com.fptufa26.ecomotion.auth.dto.response;

public record SendOtpResponse(
        boolean sent,
        long expiresInSeconds,
        long resendCooldownSeconds,
        String otp
) {
}
