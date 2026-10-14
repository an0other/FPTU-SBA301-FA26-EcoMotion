package com.fptufa26.ecomotion.auth.dto.response;

public record ResumeActivationResponse(
        String activationToken,
        long activationTokenExpiresInSeconds,
        String email,
        String fullName
) {
}
