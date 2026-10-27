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

public interface BookingQueryService {

    Optional<Booking> findBookingById(Long id);

    Optional<Booking> findBookingByCode(String bookingCode);

    Optional<Booking> findBookingByIdAndProviderId(Long id, Long providerId);

    Optional<Booking> findBookingByCodeAndProviderId(String bookingCode, Long providerId);

    List<Booking> findBookingsByProviderId(Long providerId);

    Page<Booking> findBookingsByProviderId(Long providerId, Pageable pageable);

    Booking saveBooking(Booking booking);

    List<PaymentTransaction> findTransactionsByBookingId(Long bookingId);

    List<PaymentTransaction> findTransactionsByBookingIdOrderByCreatedAtAsc(Long bookingId);

    Optional<PaymentTransaction> findTransactionByVnpTxnRef(String vnpTxnRef);

    Optional<PaymentTransaction> findTransactionByIdAndProviderId(Long id, Long providerId);

    Optional<PaymentTransaction> findTransactionByBookingIdAndType(Long bookingId, PaymentType type);

    boolean existsTransactionByBookingIdAndType(Long bookingId, PaymentType type);

    boolean existsTransactionByGatewayRef(String gatewayRef);

    PaymentTransaction saveTransaction(PaymentTransaction transaction);

    List<PaymentTransaction> findTransactionsByProviderId(Long providerId);

    Page<PaymentTransaction> findTransactionsByProviderId(Long providerId, Pageable pageable);

    Page<PaymentTransaction> findTransactionsByProviderIdAndType(Long providerId, PaymentType type, Pageable pageable);

    Page<PaymentTransaction> findTransactionsByProviderIdAndTypeAndStatus(
            Long providerId, PaymentType type, PaymentStatus status, Pageable pageable);

    List<BookingCharge> findChargesByBookingId(Long bookingId);

    List<BookingCharge> findChargesByBookingIdOrderByCreatedAtAsc(Long bookingId);

    Optional<BookingCharge> findChargeByBookingIdAndType(Long bookingId, ChargeType chargeType);

    BookingCharge saveCharge(BookingCharge charge);

    void deleteCharge(BookingCharge charge);

    Optional<BookingPolicySnapshot> findPolicySnapshotByBookingId(Long bookingId);

    BookingPolicySnapshot savePolicySnapshot(BookingPolicySnapshot snapshot);

    boolean existsByDriverIdAndStatus(Long driverId, com.fptufa26.ecomotion.booking.enums.BookingStatus status);

    Optional<Booking> findFirstByDriverIdAndStatus(Long driverId, com.fptufa26.ecomotion.booking.enums.BookingStatus status);

    List<Booking> findUpcomingDriverBookings(Long driverId, java.util.Collection<com.fptufa26.ecomotion.booking.enums.BookingStatus> statuses, java.time.Instant now);
}
