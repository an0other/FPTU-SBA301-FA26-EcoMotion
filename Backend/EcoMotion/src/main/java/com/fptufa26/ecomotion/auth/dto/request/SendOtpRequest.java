package com.fptufa26.ecomotion.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SendOtpRequest(
        @NotBlank(message = "must not be blank")
        @Email(message = "must be a well-formed email address")
        String email
) {
}
