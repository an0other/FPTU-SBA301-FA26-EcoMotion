package com.fptufa26.ecomotion.auth.dto;

import com.fptufa26.ecomotion.user.entity.Role;
import com.fptufa26.ecomotion.user.entity.UserStatus;

import java.time.Instant;

public record CurrentUserResponse(
        Long userId,
        String email,
        Role role,
        UserStatus status,
        Instant lastLoginAt
) {
}
