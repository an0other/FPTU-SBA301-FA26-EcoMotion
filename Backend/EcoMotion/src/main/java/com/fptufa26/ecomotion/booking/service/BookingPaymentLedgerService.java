package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface BookingPaymentLedgerService {

    Booking requireBooking(Long bookingId);

    Booking requireBookingForUpdate(Long bookingId);

    Booking saveBooking(Booking booking);

    Optional<BookingPolicySnapshot> findSnapshot(Long bookingId);

    Optional<BookingCharge> findCharge(Long bookingId, ChargeType chargeType);

    List<BookingCharge> listCharges(Long bookingId);

    BookingCharge saveCharge(BookingCharge charge);

    List<PaymentTransaction> listTransactions(Long bookingId);

    List<PaymentTransaction> listTransactionsAscending(Long bookingId);

    PaymentTransaction saveTransaction(PaymentTransaction transaction);

    boolean gatewayReferenceExists(String gatewayReference);

    boolean gatewayReferenceExists(Long receivingBankAccountId, String gatewayReference);

    List<Booking> listProviderBookings(Long providerId);

    List<PaymentTransaction> listProviderTransactions(Long providerId);

    Page<PaymentTransaction> pageProviderTransactions(Long providerId, PaymentType type,
                                                      PaymentStatus status, Pageable pageable);

    Optional<PaymentTransaction> findProviderTransaction(Long transactionId, Long providerId);

    Optional<PaymentTransaction> findTransactionById(Long transactionId);

    Optional<PaymentTransaction> findTransactionByIdForUpdate(Long transactionId);

    Optional<PaymentTransaction> findTransaction(Long bookingId, PaymentType type);

    boolean transactionExists(Long bookingId, PaymentType type);

    Optional<Booking> findProviderBooking(String bookingCodeOrId, Long providerId);
}
