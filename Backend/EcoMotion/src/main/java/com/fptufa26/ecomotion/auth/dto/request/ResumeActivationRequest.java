package com.fptufa26.ecomotion.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Resumes a previously started activation from the standalone activation page. The user may come
 * back long after registering, so they identify themselves by email and confirm a fresh OTP instead
 * of relying on the short-lived activation token issued at registration time.
 */
public record ResumeActivationRequest(
        @NotBlank(message = "must not be blank")
        @Email(message = "must be a well-formed email")
        String email,

        @NotBlank(message = "must not be blank")
        String otp
) {
}
