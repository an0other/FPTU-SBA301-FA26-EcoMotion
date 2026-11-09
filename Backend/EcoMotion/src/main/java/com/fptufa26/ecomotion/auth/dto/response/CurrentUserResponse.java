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
        EkycStatus ekycStatus,
        String sessionScope,
        String identityStatus,
        String providerStatus,
        String nextAction,
        java.util.List<String> capabilities
) {
    public CurrentUserResponse withAccess(String scope, String identity, String provider, String next,
            java.util.List<String> allowed) {
        return new CurrentUserResponse(userId, email, fullName, phone, role, status, ekycStatus,
                scope, identity, provider, next, allowed);
    }
}
