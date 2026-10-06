package com.fptufa26.ecomotion.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "must not be blank")
        @Email(message = "must be a well-formed email address")
        String email,

        @NotBlank(message = "must not be blank")
        String password,

        String captchaToken,

        Boolean rememberMe
) {

    public LoginRequest {
        if (rememberMe == null) {
            rememberMe = false;
        }
    }

    /** Convenience for internal callers (e.g. auto-login right after registration). */
    public LoginRequest(String email, String password, Boolean rememberMe) {
        this(email, password, null, rememberMe);
    }
}
