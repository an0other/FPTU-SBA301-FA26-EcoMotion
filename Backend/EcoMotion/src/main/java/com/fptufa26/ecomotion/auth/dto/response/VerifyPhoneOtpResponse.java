package com.fptufa26.ecomotion.auth.dto.response;

public record VerifyPhoneOtpResponse(
        boolean phoneVerified,
        String phone
) {
}
