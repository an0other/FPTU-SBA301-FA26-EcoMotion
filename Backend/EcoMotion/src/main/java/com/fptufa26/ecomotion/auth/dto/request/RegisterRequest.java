package com.fptufa26.ecomotion.auth.dto.request;

import com.fptufa26.ecomotion.user.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Self-registration payload. Deliberately minimal: only the role, the password and the terms
 * acceptance. The email comes from the verified registration token; phone, full name and identity
 * documents are captured later on the activation page.
 */
public record RegisterRequest(
        @NotBlank(message = "must not be blank")
        String registrationToken,

        @NotBlank(message = "must not be blank")
        @Size(min = 8, max = 128, message = "must be between 8 and 128 characters")
        String password,

        @NotNull(message = "must not be null")
        Role role,

        Boolean agreeTerms,

        String captchaToken
) {

    /** Convenience constructor without a captcha token (internal callers / tests). */
    public RegisterRequest(String registrationToken, String password, Role role, Boolean agreeTerms) {
        this(registrationToken, password, role, agreeTerms, null);
    }
}
