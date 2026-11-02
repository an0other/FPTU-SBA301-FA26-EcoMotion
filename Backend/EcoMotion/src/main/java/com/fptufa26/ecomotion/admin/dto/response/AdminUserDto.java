package com.fptufa26.ecomotion.admin.dto.response;

import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.enums.EkycStatus;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDto {
    private Long id;
    private String email;
    private String phone;
    private String fullName;
    private String avatarUrl;
    private Role role;
    private UserStatus status;
    private EkycStatus ekycStatus;
    private Instant createdAt;

    // Driver-specific fields when role == DRIVER
    private Long driverId;
    private String portraitUrl;
    private String licenseImageUrl;
    private String licenseNo;
    private String licenseClass;
    private LocalDate licenseExpiry;
    private Long providerId;
    private String providerDisplayName;
    private DriverStatus driverStatus;
}
