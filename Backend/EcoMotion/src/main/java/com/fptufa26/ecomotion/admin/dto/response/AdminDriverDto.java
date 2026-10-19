package com.fptufa26.ecomotion.admin.dto.response;

import com.fptufa26.ecomotion.user.enums.DriverStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDriverDto {
    private Long id;
    private Long userId;
    private String userEmail;
    private String userFullName;
    private String userPhone;
    private Long providerId;
    private String providerDisplayName;
    private String licenseNo;
    private String licenseClass;
    private LocalDate licenseExpiry;
    private String portraitUrl;
    private String licenseImageUrl;
    private DriverStatus status;
    private String rejectReason;
    private BigDecimal ratingAvg;
    private Integer ratingCount;
    private Instant createdAt;
}
