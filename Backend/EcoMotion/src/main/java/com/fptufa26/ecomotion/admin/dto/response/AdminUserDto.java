package com.fptufa26.ecomotion.admin.dto.response;

import com.fptufa26.ecomotion.user.enums.EkycStatus;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

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
}
