package com.fptufa26.ecomotion.finance.service;

import com.fptufa26.ecomotion.finance.dto.request.ConfirmCommissionPaymentRequest;
import com.fptufa26.ecomotion.finance.dto.request.ReportCommissionPaymentRequest;
import com.fptufa26.ecomotion.finance.dto.response.CommissionOverviewResponse;
import com.fptufa26.ecomotion.finance.dto.response.CommissionStatementResponse;
import com.fptufa26.ecomotion.finance.enums.CommissionStatementStatus;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/** Hoa hồng hằng tháng nhà xe nộp cho nền tảng. */
public interface CommissionService {

    /** Màn hoa hồng của nhà xe hiện tại. */
    CommissionOverviewResponse overview(Long providerUserId);

    CommissionStatementResponse providerStatement(Long providerUserId, Long statementId);

    CommissionStatementResponse reportPayment(Long providerUserId, Long statementId, ReportCommissionPaymentRequest request);

    /** Admin: danh sách bảng kê (lọc theo tháng YYYY-MM và trạng thái, có thể null). */
    List<CommissionStatementResponse> adminStatements(String periodMonth, CommissionStatementStatus status);

    CommissionStatementResponse adminStatement(Long statementId);

    /** Admin không thấy tiền trong sao kê: trả bảng kê về trạng thái chưa nộp. */
    CommissionStatementResponse rejectReport(Long statementId, String note);

    CommissionStatementResponse confirmPayment(Long adminUserId, Long statementId, ConfirmCommissionPaymentRequest request);

    /** Lập bảng kê cho mọi nhà xe của tháng (idempotent). Trả về số bảng kê mới tạo. */
    int generate(YearMonth month);

    /** Chuyển các bảng kê quá hạn chưa nộp đủ sang OVERDUE (idempotent). */
    int markOverdue();

    /** Nhà xe đang bị khoá nhận đơn mới vì nợ hoa hồng quá hạn quá số ngày ân hạn. */
    boolean isBookingBlocked(Long providerId);

    Set<Long> blockedProviderIds();
}
