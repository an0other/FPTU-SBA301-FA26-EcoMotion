package com.fptufa26.ecomotion.finance.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateVnpayPaymentUrlRequest(
        @NotBlank(message = "Mã đơn thuê là bắt buộc")
        String bookingCode,

        String paymentType,

        String bankCode,

        String language
) {
}
