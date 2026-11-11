package com.fptufa26.ecomotion.finance.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** Màn "Hoa hồng" của nhà xe: kỳ đang chạy (tạm tính), các bảng kê đã lập và tình trạng khoá nhận đơn. */
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
