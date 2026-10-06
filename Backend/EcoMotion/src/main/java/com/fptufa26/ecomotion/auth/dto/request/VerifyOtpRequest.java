package com.fptufa26.ecomotion.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
        @NotBlank(message = "must not be blank")
        @Email(message = "must be a well-formed email address")
        String email,

        @NotBlank(message = "must not be blank")
        @Pattern(regexp = "^\\d{6}$", message = "must be a 6-digit code")
        String otp
) {
}
