package com.fptufa26.ecomotion.auth.dto.response;

public record RegisterResponse(
        boolean created,
        String message,
        String activationToken,
        long activationTokenExpiresInSeconds
) {
}
