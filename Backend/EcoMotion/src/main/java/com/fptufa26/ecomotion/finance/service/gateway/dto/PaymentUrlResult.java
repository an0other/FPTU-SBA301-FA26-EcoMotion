package com.fptufa26.ecomotion.finance.service.gateway.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentUrlResult(
        String paymentUrl,
        String txnRef,
        BigDecimal amount,
        Instant expireTime
) {
}
