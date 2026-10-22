package com.fptufa26.ecomotion.user.dto.response;

public record ProviderDriverStatsResponse(
        long total,
        long approved,
        long pending,
        long suspended,
        long rejected
) {
}
