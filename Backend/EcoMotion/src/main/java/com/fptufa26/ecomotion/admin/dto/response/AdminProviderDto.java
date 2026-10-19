package com.fptufa26.ecomotion.admin.dto.response;

import com.fptufa26.ecomotion.user.enums.ProviderStatus;
import com.fptufa26.ecomotion.user.enums.ProviderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminProviderDto {
    private Long id;
    private Long userId;
    private String userEmail;
    private String userFullName;
    private String userPhone;
    private ProviderType providerType;
    private String displayName;
    private String hotline;
    private String depotAddress;
    private BigDecimal commissionRate;
    private BigDecimal depositRatio;
    private String bankName;
    private String bankAccountNo;
    private String bankAccountHolder;
    private String businessLicenseNo;
    private String taxCode;
    private ProviderStatus status;
    private BigDecimal ratingAvg;
    private Integer ratingCount;
    private Instant createdAt;
}
