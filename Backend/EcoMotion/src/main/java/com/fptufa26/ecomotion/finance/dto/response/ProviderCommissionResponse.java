package com.fptufa26.ecomotion.finance.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record ProviderCommissionResponse(
        Long bookingId,
        String bookingCode,
        BigDecimal baseRental,
        BigDecimal totalConfirmedRevenue,
        BigDecimal commissionRate,
        BigDecimal commissionAmount,
        String commissionStatus,
        Instant bookingDate
) {
}
