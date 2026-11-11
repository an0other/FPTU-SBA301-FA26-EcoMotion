package com.fptufa26.ecomotion.finance.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CommissionOverviewResponse(
        CommissionStatementResponse currentPeriod,
        List<CommissionStatementResponse> statements,
        boolean bookingBlocked,
        BigDecimal outstandingAmount,
        PlatformBank platformBank,
        int graceDays
) {
    public record PlatformBank(String bankCode, String bankName, String accountNumber, String accountHolder) {
    }
}
