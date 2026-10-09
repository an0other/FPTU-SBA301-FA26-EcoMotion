package com.fptufa26.ecomotion.auth.dto.request;

import com.fptufa26.ecomotion.user.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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

    public RegisterRequest(String registrationToken, String password, Role role, Boolean agreeTerms) {
        this(registrationToken, password, role, agreeTerms, null);
    }
}
