package com.fptufa26.ecomotion.auth.dto;

public record RefreshResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
