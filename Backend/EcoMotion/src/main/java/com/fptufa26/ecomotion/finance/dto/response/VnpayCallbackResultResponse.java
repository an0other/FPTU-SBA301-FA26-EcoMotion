package com.fptufa26.ecomotion.finance.dto.response;

import java.math.BigDecimal;

public record VnpayCallbackResultResponse(
        String bookingCode,
        String transactionCode,
        String vnpTxnRef,
        String vnpTransactionNo,
        BigDecimal amount,
        String bankCode,
        String cardType,
        String payDate,
        String responseCode,
        boolean success,
        String message,
        String status,
        boolean retryAllowed
) {
    public VnpayCallbackResultResponse(
            String bookingCode,
            String vnpTxnRef,
            String vnpTransactionNo,
            BigDecimal amount,
            String bankCode,
            String cardType,
            String payDate,
            String responseCode,
            boolean success,
            String message,
            String status,
            boolean retryAllowed
    ) {
        this(bookingCode, null, vnpTxnRef, vnpTransactionNo, amount, bankCode, cardType, payDate, responseCode, success, message, status, retryAllowed);
    }
}
