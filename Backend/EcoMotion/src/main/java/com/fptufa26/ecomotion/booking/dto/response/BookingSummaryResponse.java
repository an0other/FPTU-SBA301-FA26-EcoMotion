package com.fptufa26.ecomotion.booking.dto.response;

public record BookingSummaryResponse(
        long total,
        long pending,
        long accepted,
        long confirmed,
        long inProgress,
        long completed,
        long rejected,
        long cancelled
) {
}
