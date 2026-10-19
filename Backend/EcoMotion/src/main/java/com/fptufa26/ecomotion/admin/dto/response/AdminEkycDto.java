package com.fptufa26.ecomotion.admin.dto.response;

import com.fptufa26.ecomotion.user.enums.EkycStatus;
import com.fptufa26.ecomotion.user.enums.Role;
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
public class AdminEkycDto {
    private Long userId;
    private String email;
    private String fullName;
    private String phone;
    private Role role;
    private EkycStatus ekycStatus;
    private String cccdNo;
    private LocalDate dateOfBirth;
    private String gender;
    private String address;
    private String cccdImageUrl;
    private String cccdImageBackUrl;
    private String portraitUrl;
    private Instant createdAt;
}
