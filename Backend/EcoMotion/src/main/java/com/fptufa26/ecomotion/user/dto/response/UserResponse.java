package com.fptufa26.ecomotion.user.dto.response;

import com.fptufa26.ecomotion.user.entity.Role;
import com.fptufa26.ecomotion.user.entity.UserStatus;

public record UserResponse(
        Long userId,
        String email,
        Role role,
        UserStatus status
) {
}
