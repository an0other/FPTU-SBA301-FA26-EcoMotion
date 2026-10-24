package com.fptufa26.ecomotion.finance.dto.response;

import com.fptufa26.ecomotion.booking.dto.response.BookingChargeLineResponse;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;

import java.math.BigDecimal;
import java.util.List;

public record RenterSettlementResponse(
        String bookingCode,
        BookingStatus status,
        BigDecimal baseRental,
        List<BookingChargeLineResponse> charges,
        BigDecimal totalCharges,
        BigDecimal depositPaid,
        BigDecimal otherPaid,
        BigDecimal totalPaid,
        BigDecimal discountsOrRefunds,
        BigDecimal finalPayable,
        BigDecimal overpayment,
        boolean eligibleForPayment,
        boolean isSettled,
        String message
) {
}
