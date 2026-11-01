package com.fptufa26.ecomotion.user.dto.response;

public record ProviderDriverStatsResponse(
        long total,
        long approved,
        long pending,
        long suspended,
        long rejected,
        long available,
        long onTrip,
        long scheduled,
        long unavailable,
        long offline
) {
    public ProviderDriverStatsResponse(long total, long approved, long pending, long suspended, long rejected) {
        this(total, approved, pending, suspended, rejected, 0, 0, 0, 0, 0);
    }
}
