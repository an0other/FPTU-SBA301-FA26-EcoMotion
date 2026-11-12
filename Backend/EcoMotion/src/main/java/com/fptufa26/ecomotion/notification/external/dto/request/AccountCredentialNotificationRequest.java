package com.fptufa26.ecomotion.notification.external.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AccountCredentialNotificationRequest(
        @NotBlank @Email String email,
        @NotBlank String fullName,
        @NotBlank String temporaryPassword,
        @NotBlank String changePasswordUrl,
        @NotBlank String providerName
) {
}

