package com.fptufa26.ecomotion.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Requests the email OTP for registration. Only the email is needed: the phone number is no longer
 * collected at sign-up (it is verified later on the activation page).
 */
public record SendOtpRequest(
        @NotBlank(message = "must not be blank")
        @Email(message = "must be a well-formed email address")
        String email
) {
}
