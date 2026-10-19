package com.fptufa26.ecomotion.finance.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ProviderCommissionSummaryResponse(
        BigDecimal totalCommissionDue,
        BigDecimal totalCommissionPaid,
        BigDecimal averageCommissionRate,
        List<ProviderCommissionResponse> commissions
) {
}
