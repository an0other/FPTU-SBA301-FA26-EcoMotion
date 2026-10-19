package com.fptufa26.ecomotion.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record SimulatePaymentRequest(
        @NotBlank(message = "Kịch bản mô phỏng không được để trống")
        String scenario,

        BigDecimal simulatedAmount,

        String bankTransactionRef,

        String note
) {
}
