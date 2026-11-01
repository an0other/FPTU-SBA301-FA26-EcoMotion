package com.fptufa26.ecomotion.finance.service.gateway.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentUrlCommand(
        String bookingCode,
        String txnRef,
        BigDecimal amount,
        String orderInfo,
        String clientIp,
        String language,
        String bankCode,
        Instant createDate,
        Instant expireDate
) {
}
