package com.fptufa26.ecomotion.finance.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ConfirmPaymentRequest(
        @DecimalMin(value = "1000", message = "Số tiền thực nhận tối thiểu 1.000 ₫")
        BigDecimal receivedAmount,

        @Size(max = 100, message = "Mã tham chiếu ngân hàng tối đa 100 ký tự")
        String bankTransactionRef,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note
) {
}
