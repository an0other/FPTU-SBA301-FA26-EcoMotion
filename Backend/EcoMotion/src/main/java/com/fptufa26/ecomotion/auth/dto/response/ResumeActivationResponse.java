package com.fptufa26.ecomotion.auth.dto.response;

/** A freshly issued activation token for an existing PENDING account that is being resumed. */
public record ResumeActivationResponse(
        String activationToken,
        long activationTokenExpiresInSeconds,
        String email,
        String fullName
) {
}
