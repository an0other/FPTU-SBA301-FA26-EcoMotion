package com.fptufa26.ecomotion.finance.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record SubmitProofRequest(
        @NotBlank(message = "Ảnh bằng chứng thanh toán không được để trống")
        @Size(max = 500, message = "Đường dẫn ảnh tối đa 500 ký tự")
        String proofImageUrl,

        @NotNull(message = "Số tiền đã chuyển không được để trống")
        @DecimalMin(value = "1000", message = "Số tiền đã chuyển tối thiểu 1.000 ₫")
        BigDecimal transferredAmount,

        @Size(max = 100, message = "Mã giao dịch ngân hàng tối đa 100 ký tự")
        String transactionReference,

        Instant transferredAt,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note
) {
}
