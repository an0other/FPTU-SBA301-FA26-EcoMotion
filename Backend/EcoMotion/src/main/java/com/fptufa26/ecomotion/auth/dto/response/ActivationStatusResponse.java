package com.fptufa26.ecomotion.auth.dto.response;

import com.fptufa26.ecomotion.user.enums.EkycStatus;
import com.fptufa26.ecomotion.user.enums.Role;

public record ActivationStatusResponse(
        String email,
        String role,
        String status,
        String ekycStatus,
        boolean phoneVerified,
        boolean cccdScanned,
        String fullName,
        String cccdNo,
        String dateOfBirth,
        String gender,
        String address
) {
}
