package com.fptufa26.ecomotion.otp.dto.response;

public record OtpChallenge(
        String code,
        long expiresInSeconds,
        long resendCooldownSeconds
) {
}
