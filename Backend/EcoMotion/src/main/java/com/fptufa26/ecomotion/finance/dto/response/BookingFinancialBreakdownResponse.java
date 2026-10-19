package com.fptufa26.ecomotion.finance.dto.response;

import com.fptufa26.ecomotion.booking.dto.response.BookingChargeResponse;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;

import java.math.BigDecimal;
import java.util.List;

public record BookingFinancialBreakdownResponse(
        Long bookingId,
        String bookingCode,
        BookingStatus bookingStatus,
        BigDecimal baseRental,
        BigDecimal depositRequired,
        BigDecimal depositPaid,
        BigDecimal approvedCharges,
        BigDecimal approvedReductions,
        BigDecimal totalPayable,
        BigDecimal netConfirmedPayments,
        BigDecimal remainingAmount,
        BigDecimal overpaidAmount,
        List<BookingChargeResponse> charges,
        List<ProviderTransactionResponse> transactions,
        List<PaymentRequestResponse> paymentRequests
) {
}
