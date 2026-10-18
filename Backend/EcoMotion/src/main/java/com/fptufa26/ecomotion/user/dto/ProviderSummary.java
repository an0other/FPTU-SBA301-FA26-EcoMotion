package com.fptufa26.ecomotion.user.dto;

import com.fptufa26.ecomotion.user.enums.ProviderType;

import java.math.BigDecimal;

public record ProviderSummary(
        Long id,
        String displayName,
        ProviderType providerType,
        String hotline,
        String depotAddress,
        BigDecimal depotLatitude,
        BigDecimal depotLongitude,
        BigDecimal ratingAvg,
        Integer ratingCount
) {
}
