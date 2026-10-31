package com.fptufa26.ecomotion.finance.service.gateway.dto;

import java.math.BigDecimal;

public record PaymentVerificationResult(
        boolean validSignature,
        boolean success,
        String txnRef,
        String gatewayTransactionNo,
        String responseCode,
        BigDecimal amount,
        String bankCode,
        String cardType,
        String payDate,
        String message
) {
}
