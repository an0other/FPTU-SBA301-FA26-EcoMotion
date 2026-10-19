package com.fptufa26.ecomotion.finance.dto.response;

import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;

public record SimulatePaymentResponse(
        boolean success,
        String scenario,
        String message,
        Long paymentRequestId,
        Long paymentTransactionId,
        PaymentRequestStatus newStatus
) {
}
