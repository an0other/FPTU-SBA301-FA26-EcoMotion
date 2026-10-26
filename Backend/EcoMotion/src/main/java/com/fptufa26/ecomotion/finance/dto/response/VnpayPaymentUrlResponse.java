package com.fptufa26.ecomotion.finance.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record VnpayPaymentUrlResponse(
        String paymentUrl,
        String vnpTxnRef,
        BigDecimal amount,
        String bookingCode,
        Instant expiresAt
) {
}
