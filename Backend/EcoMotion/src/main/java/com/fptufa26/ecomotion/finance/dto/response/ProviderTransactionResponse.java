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
        String receiptUrl,
        String processingNote,
        Instant paidAt,
        Instant createdAt,
        /** Yêu cầu thanh toán mà giao dịch thuộc về (để khách đối chiếu "nhà xe xác nhận đã nhận"). */
        Long paymentRequestId,
        com.fptufa26.ecomotion.finance.enums.PaymentRequestType paymentRequestType,
        /** Vai trò người xác nhận (PROVIDER/ADMIN) — null với giao dịch cổng thanh toán. */
        com.fptufa26.ecomotion.user.enums.Role confirmedByRole
) {
}
