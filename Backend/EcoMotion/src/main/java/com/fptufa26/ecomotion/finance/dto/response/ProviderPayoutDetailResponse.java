package com.fptufa26.ecomotion.finance.dto.response;

import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ProviderPayoutDetailResponse(
        Long id,
        String bookingCode,
        String vehicleName,
        String licensePlate,
        BigDecimal grossAmount,
        BigDecimal platformFee,
        BigDecimal netAmount,
        String currency,
        PaymentStatus status,
        PaymentMethod method,
        String gatewayRef,
        String receiptUrl,
        DestinationBankSnapshotResponse destinationBank,
        Instant createdAt,
        Instant paidAt,
        boolean mock
) {
}
