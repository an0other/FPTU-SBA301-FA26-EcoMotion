package com.fptufa26.ecomotion.auth.dto.response;

/**
 * Response of the activation phone-OTP step. {@code otp} is the <b>mock</b> code: there is no SMS
 * gateway, so the code is returned (and displayed) to let the flow be completed without one.
 */
public record SendPhoneOtpResponse(
        boolean sent,
        String otp,
        long expiresInSeconds,
        long resendCooldownSeconds
) {
}
