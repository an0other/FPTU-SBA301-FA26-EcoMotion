package com.fptufa26.ecomotion.finance.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Admin xác nhận số tiền hoa hồng thực nhận theo sao kê. */
public record ConfirmCommissionPaymentRequest(
        @NotNull(message = "Nhập số tiền thực nhận") @DecimalMin(value = "1", message = "Số tiền thực nhận phải lớn hơn 0") BigDecimal receivedAmount,
        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note
) {
}
