package com.fptufa26.ecomotion.finance.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateRefundRequest(
        @NotBlank(message = "Mã đơn thuê là bắt buộc")
        String bookingCode,

        @NotNull(message = "Số tiền hoàn là bắt buộc")
        @DecimalMin(value = "1000", message = "Số tiền hoàn tối thiểu là 1.000 VNĐ")
        BigDecimal amount,

        @NotBlank(message = "Lý do hoàn tiền là bắt buộc")
        @Size(max = 500, message = "Lý do hoàn tiền không quá 500 ký tự")
        String reason,

        @Size(max = 500, message = "Đường dẫn minh chứng không quá 500 ký tự")
        String evidenceUrl
) {
}
