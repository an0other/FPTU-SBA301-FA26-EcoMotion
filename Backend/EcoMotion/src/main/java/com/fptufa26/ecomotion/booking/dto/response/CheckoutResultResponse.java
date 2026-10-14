package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;

import java.math.BigDecimal;
import java.time.Instant;

public record CheckoutResultResponse(
        String bookingCode,
        BookingStatus status,
        PaymentType paymentType,
        BigDecimal amountPaid,
        Instant endTime
) {
}
