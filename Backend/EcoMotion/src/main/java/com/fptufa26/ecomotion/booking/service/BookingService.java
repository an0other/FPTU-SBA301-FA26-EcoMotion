package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface BookingService {

    List<Booking> listAdminBookings(BookingStatus status, String keyword);

    Optional<Booking> getBookingById(Long bookingId);

    List<BookingCharge> getChargesForBooking(Long bookingId);

    List<PaymentTransaction> listTransactions(PaymentStatus status, PaymentType type);

    List<PaymentTransaction> getTransactionsForBooking(Long bookingId);

    PaymentTransaction processMockRefund(Long bookingId, BigDecimal amount, String reason);

    long countBookingsByStatus(BookingStatus status);

    long countTotalBookings();

    BigDecimal sumTotalRevenue();
}
