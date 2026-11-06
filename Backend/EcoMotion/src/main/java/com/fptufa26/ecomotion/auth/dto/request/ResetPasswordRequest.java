package com.fptufa26.ecomotion.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(max = 12) String otp,
        @NotBlank @Size(min = 8, max = 128) String password) {
}
