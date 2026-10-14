package com.fptufa26.ecomotion.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SendPhoneOtpRequest(
        @NotBlank(message = "must not be blank")
        String activationToken,

        @NotBlank(message = "must not be blank")
        @Pattern(regexp = "^0\\d{9,10}$", message = "must be a valid Vietnamese phone number")
        String phone
) {
}
