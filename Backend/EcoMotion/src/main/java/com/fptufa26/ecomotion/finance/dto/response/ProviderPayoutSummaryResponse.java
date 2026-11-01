package com.fptufa26.ecomotion.finance.dto.response;

import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ProviderPayoutSummaryResponse(
        Long id,
        String bookingCode,
        BigDecimal grossAmount,
        BigDecimal platformFee,
        BigDecimal netAmount,
        String currency,
        PaymentStatus status,
        PaymentMethod method,
        String gatewayRef,
        DestinationBankSnapshotResponse destinationBank,
        Instant createdAt,
        Instant paidAt,
        com.fptufa26.ecomotion.finance.enums.PaymentVerificationMethod verificationMethod
) {
}
