package com.fptufa26.ecomotion.auth.dto.response;

public record RefreshResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
