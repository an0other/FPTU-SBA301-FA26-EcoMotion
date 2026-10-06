package com.fptufa26.ecomotion.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record VerifyPhoneOtpRequest(
        @NotBlank(message = "must not be blank")
        String activationToken,

        @NotBlank(message = "must not be blank")
        String otp
) {
}
