package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.booking.service.BookingQueryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingQueryServiceImpl implements BookingQueryService {

    @Override
    @Transactional(readOnly = true)
    public List<com.fptufa26.ecomotion.booking.dto.response.PaymentReconciliationTarget> pendingGatewayPayments(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Reconciliation batch must be between 1 and 100");
        return paymentTransactionRepository.findByMethodAndStatusAndVnpTxnRefIsNotNullOrderByUpdatedAtAscCreatedAtAsc(
                com.fptufa26.ecomotion.booking.enums.PaymentMethod.VNPAY, PaymentStatus.PENDING,
                org.springframework.data.domain.PageRequest.of(0, limit)).stream()
                .map(t -> new com.fptufa26.ecomotion.booking.dto.response.PaymentReconciliationTarget(t.getBooking().getBookingCode(), t.getVnpTxnRef())).toList();
    }

    private final BookingRepository bookingRepository;
    private final EntityManager entityManager;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final BookingChargeRepository bookingChargeRepository;
    private final BookingPolicySnapshotRepository bookingPolicySnapshotRepository;

    @Override
    @Transactional
    public Booking lockBookingForPayment(Long id) {
        Booking booking = entityManager.find(Booking.class, id);
        if (booking == null) throw new com.fptufa26.ecomotion.common.exception.NotFoundException("Không tìm thấy booking");
        entityManager.refresh(booking, LockModeType.PESSIMISTIC_WRITE);
        return booking;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Booking> findBookingById(Long id) {
        return bookingRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Booking> findBookingByCode(String bookingCode) {
        return bookingRepository.findByBookingCode(bookingCode);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Booking> findBookingByIdAndProviderId(Long id, Long providerId) {
        return bookingRepository.findByIdAndVehicle_Provider_Id(id, providerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Booking> findBookingByCodeAndProviderId(String bookingCode, Long providerId) {
        return bookingRepository.findByBookingCodeAndVehicle_Provider_Id(bookingCode, providerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Booking> findBookingsByProviderId(Long providerId) {
        return bookingRepository.findByVehicle_Provider_Id(providerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Booking> findBookingsByProviderId(Long providerId, Pageable pageable) {
        return bookingRepository.findByVehicle_Provider_Id(providerId, pageable);
    }

    @Override
    @Transactional
    public Booking saveBooking(Booking booking) {
        return bookingRepository.save(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransaction> findTransactionsByBookingId(Long bookingId) {
        return paymentTransactionRepository.findByBooking_Id(bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransaction> findTransactionsByBookingIdOrderByCreatedAtAsc(Long bookingId) {
        return paymentTransactionRepository.findByBooking_IdOrderByCreatedAtAsc(bookingId);
    }

    @Override
    @Transactional
    public Optional<PaymentTransaction> findTransactionByVnpTxnRefForUpdate(String vnpTxnRef) {
        var transaction = paymentTransactionRepository.findByVnpTxnRefForUpdate(vnpTxnRef);
        transaction.ifPresent(tx -> entityManager.refresh(tx, LockModeType.PESSIMISTIC_WRITE));
        return transaction;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentTransaction> findTransactionByVnpTxnRef(String vnpTxnRef) {
        return paymentTransactionRepository.findByVnpTxnRef(vnpTxnRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentTransaction> findTransactionByIdAndProviderId(Long id, Long providerId) {
        return paymentTransactionRepository.findByIdAndBooking_Vehicle_Provider_Id(id, providerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentTransaction> findTransactionByBookingIdAndType(Long bookingId, PaymentType type) {
        return paymentTransactionRepository.findByBooking_IdAndType(bookingId, type);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsTransactionByBookingIdAndType(Long bookingId, PaymentType type) {
        return paymentTransactionRepository.existsByBooking_IdAndType(bookingId, type);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsTransactionByGatewayRef(String gatewayRef) {
        return paymentTransactionRepository.existsByGatewayRef(gatewayRef);
    }

    @Override
    @Transactional
    public PaymentTransaction saveTransaction(PaymentTransaction transaction) {
        return paymentTransactionRepository.save(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransaction> findTransactionsByProviderId(Long providerId) {
        return paymentTransactionRepository.findByBooking_Vehicle_Provider_Id(providerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentTransaction> findTransactionsByProviderId(Long providerId, Pageable pageable) {
        return paymentTransactionRepository.findByBooking_Vehicle_Provider_Id(providerId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentTransaction> findTransactionsByProviderIdAndType(Long providerId, PaymentType type, Pageable pageable) {
        return paymentTransactionRepository.findByBooking_Vehicle_Provider_IdAndType(providerId, type, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentTransaction> findTransactionsByProviderIdAndTypeAndStatus(
            Long providerId, PaymentType type, PaymentStatus status, Pageable pageable) {
        return paymentTransactionRepository.findByBooking_Vehicle_Provider_IdAndTypeAndStatus(
                providerId, type, status, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingCharge> findChargesByBookingId(Long bookingId) {
        return bookingChargeRepository.findByBooking_Id(bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingCharge> findChargesByBookingIdOrderByCreatedAtAsc(Long bookingId) {
        return bookingChargeRepository.findByBooking_IdOrderByCreatedAtAsc(bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BookingCharge> findChargeByBookingIdAndType(Long bookingId, ChargeType chargeType) {
        return bookingChargeRepository.findByBooking_IdAndChargeType(bookingId, chargeType);
    }

    @Override
    @Transactional
    public BookingCharge saveCharge(BookingCharge charge) {
        return bookingChargeRepository.save(charge);
    }

    @Override
    @Transactional
    public void deleteCharge(BookingCharge charge) {
        bookingChargeRepository.delete(charge);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BookingPolicySnapshot> findPolicySnapshotByBookingId(Long bookingId) {
        return bookingPolicySnapshotRepository.findByBooking_Id(bookingId);
    }

    @Override
    @Transactional
    public BookingPolicySnapshot savePolicySnapshot(BookingPolicySnapshot snapshot) {
        return bookingPolicySnapshotRepository.save(snapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByDriverIdAndStatus(Long driverId, com.fptufa26.ecomotion.booking.enums.BookingStatus status) {
        return bookingRepository.existsByDriver_IdAndStatus(driverId, status);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Booking> findFirstByDriverIdAndStatus(Long driverId, com.fptufa26.ecomotion.booking.enums.BookingStatus status) {
        return bookingRepository.findFirstByDriver_IdAndStatus(driverId, status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Booking> findUpcomingDriverBookings(Long driverId, java.util.Collection<com.fptufa26.ecomotion.booking.enums.BookingStatus> statuses, java.time.Instant now) {
        return bookingRepository.findUpcomingDriverBookings(driverId, statuses, now);
    }
}
