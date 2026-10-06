package com.fptufa26.ecomotion.auth.dto.response;

import com.fptufa26.ecomotion.user.entity.EkycStatus;
import com.fptufa26.ecomotion.user.entity.Role;
import com.fptufa26.ecomotion.user.entity.UserStatus;

public record CurrentUserResponse(
        Long userId,
        String email,
        String fullName,
        String phone,
        Role role,
        UserStatus status,
        EkycStatus ekycStatus
) {
}
