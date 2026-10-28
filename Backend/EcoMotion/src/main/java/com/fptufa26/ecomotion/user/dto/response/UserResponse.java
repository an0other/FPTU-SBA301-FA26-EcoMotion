package com.fptufa26.ecomotion.user.dto.response;

import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;

public record UserResponse(
        Long userId,
        String email,
        String fullName,
        Role role,
        UserStatus status
) {
}
