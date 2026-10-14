package com.fptufa26.ecomotion.auth.dto.response;

import com.fptufa26.ecomotion.user.enums.EkycStatus;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;

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
