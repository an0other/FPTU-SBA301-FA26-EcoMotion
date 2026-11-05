package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingPaymentStatus;
import java.math.BigDecimal;
import java.util.List;

public interface BookingPaymentStateService {
    State evaluate(Booking booking, List<PaymentTransaction> transactions);

    record State(BookingPaymentStatus status, BigDecimal appliedPaid, BigDecimal refunded,
                 BigDecimal pendingRefund, BigDecimal remaining) {}
}
