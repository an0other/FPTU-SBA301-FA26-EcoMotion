package com.fptufa26.ecomotion.auth.dto.response;

import com.fptufa26.ecomotion.user.dto.response.UserResponse;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
}
