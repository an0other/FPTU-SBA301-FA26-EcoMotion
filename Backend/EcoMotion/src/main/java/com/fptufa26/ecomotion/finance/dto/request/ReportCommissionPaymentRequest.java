package com.fptufa26.ecomotion.finance.dto.request;

import jakarta.validation.constraints.Size;

/** Nhà xe báo đã chuyển khoản hoa hồng (mã giao dịch và ghi chú tuỳ chọn). */
public record ReportCommissionPaymentRequest(
        @Size(max = 100, message = "Mã giao dịch tối đa 100 ký tự") String paymentReference,
        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note
) {
}
