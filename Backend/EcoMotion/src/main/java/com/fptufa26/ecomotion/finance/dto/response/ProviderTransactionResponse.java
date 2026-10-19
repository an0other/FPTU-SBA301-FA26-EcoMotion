package com.fptufa26.ecomotion.finance.dto.response;

import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.finance.enums.PaymentVerificationMethod;

import java.math.BigDecimal;
import java.time.Instant;

public record ProviderTransactionResponse(
        Long id,
        Long bookingId,
        String bookingCode,
        PaymentType type,
        BigDecimal amount,
        PaymentDirection direction,
        PaymentMethod method,
        PaymentStatus status,
        String gatewayRef,
        PaymentVerificationMethod verificationMethod,
        String bankCode,
        String bankName,
        String accountNumberMasked,
        String accountHolder,
        Instant paidAt,
        Instant createdAt
) {
}
