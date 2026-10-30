package com.fptufa26.ecomotion.finance.dto.response;

import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.finance.enums.PaymentReceiptStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentVerificationMethod;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentReceiptResponse(
        PaymentReceiptStatus status,
        String bookingCode,
        String transactionCode,
        String vnpTransactionNo,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        PaymentVerificationMethod verificationMethod,
        Instant paidAt
) {}
