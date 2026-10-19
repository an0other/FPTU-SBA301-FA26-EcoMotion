package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;

import java.util.List;

public record BookingChargesResponse(
        String bookingCode,
        BookingStatus bookingStatus,
        String vehicleName,
        PlanType planType,
        BookingPaymentSummaryResponse summary,
        List<BookingChargeResponse> charges,
        List<PaymentHistoryResponse> transactions
) {
}