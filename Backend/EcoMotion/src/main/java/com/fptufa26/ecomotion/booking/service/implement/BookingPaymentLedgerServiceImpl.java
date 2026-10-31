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
import com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService;
import com.fptufa26.ecomotion.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingPaymentLedgerServiceImpl implements BookingPaymentLedgerService {

    private final BookingRepository bookingRepository;
    private final BookingPolicySnapshotRepository snapshotRepository;
    private final BookingChargeRepository chargeRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final jakarta.persistence.EntityManager entityManager;

    @Override
    public Booking requireBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy booking"));
    }

    @Override
    public Booking requireBookingForUpdate(Long bookingId) {
        Booking booking = entityManager.find(Booking.class, bookingId);
        if (booking == null) throw new NotFoundException("Không tìm thấy booking");
        entityManager.refresh(booking, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return booking;
    }

    @Override
    @Transactional
    public Booking saveBooking(Booking booking) {
        return bookingRepository.save(booking);
    }

    @Override
    public Optional<BookingPolicySnapshot> findSnapshot(Long bookingId) {
        return snapshotRepository.findByBooking_Id(bookingId);
    }

    @Override
    public Optional<BookingCharge> findCharge(Long bookingId, ChargeType chargeType) {
        return chargeRepository.findByBooking_IdAndChargeType(bookingId, chargeType);
    }

    @Override
    public List<BookingCharge> listCharges(Long bookingId) {
        return chargeRepository.findByBooking_IdOrderByCreatedAtAsc(bookingId);
    }

    @Override
    @Transactional
    public BookingCharge saveCharge(BookingCharge charge) {
        return chargeRepository.save(charge);
    }

    @Override
    public List<PaymentTransaction> listTransactions(Long bookingId) {
        return transactionRepository.findByBooking_Id(bookingId);
    }

    @Override
    public List<PaymentTransaction> listTransactionsAscending(Long bookingId) {
        return transactionRepository.findByBooking_IdOrderByCreatedAtAsc(bookingId);
    }

    @Override
    @Transactional
    public PaymentTransaction saveTransaction(PaymentTransaction transaction) {
        return transactionRepository.save(transaction);
    }

    @Override
    public boolean gatewayReferenceExists(String gatewayReference) {
        return transactionRepository.existsByGatewayRef(gatewayReference);
    }

    @Override
    public boolean gatewayReferenceExists(Long receivingBankAccountId, String gatewayReference) {
        return transactionRepository.existsByReceivingBankAccount_IdAndGatewayRef(
                receivingBankAccountId, gatewayReference);
    }

    @Override
    public List<Booking> listProviderBookings(Long providerId) {
        return bookingRepository.findByVehicle_Provider_Id(providerId, Pageable.unpaged()).getContent();
    }

    @Override
    public List<PaymentTransaction> listProviderTransactions(Long providerId) {
        return transactionRepository.findByBooking_Vehicle_Provider_Id(providerId);
    }

    @Override
    public Page<PaymentTransaction> pageProviderTransactions(Long providerId, PaymentType type,
                                                             PaymentStatus status, Pageable pageable) {
        if (type != null && status != null) {
            return transactionRepository.findByBooking_Vehicle_Provider_IdAndTypeAndStatus(
                    providerId, type, status, pageable);
        }
        if (type != null) {
            return transactionRepository.findByBooking_Vehicle_Provider_IdAndType(providerId, type, pageable);
        }
        return transactionRepository.findByBooking_Vehicle_Provider_Id(providerId, pageable);
    }

    @Override
    public Optional<PaymentTransaction> findProviderTransaction(Long transactionId, Long providerId) {
        return transactionRepository.findByIdAndBooking_Vehicle_Provider_Id(transactionId, providerId);
    }

    @Override
    @Transactional
    public Optional<PaymentTransaction> findTransactionByIdForUpdate(Long transactionId) {
        var transaction = transactionRepository.findByIdForUpdate(transactionId);
        transaction.ifPresent(t -> entityManager.refresh(t, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));
        return transaction;
    }

    @Override
    public Optional<PaymentTransaction> findTransactionById(Long transactionId) {
        return transactionRepository.findById(transactionId);
    }

    @Override
    public Optional<PaymentTransaction> findTransaction(Long bookingId, PaymentType type) {
        return transactionRepository.findByBooking_IdAndType(bookingId, type);
    }

    @Override
    public boolean transactionExists(Long bookingId, PaymentType type) {
        return transactionRepository.existsByBooking_IdAndType(bookingId, type);
    }

    @Override
    public Optional<Booking> findProviderBooking(String bookingCodeOrId, Long providerId) {
        String bookingCode = bookingCodeOrId == null
                ? ""
                : bookingCodeOrId.trim().toUpperCase(java.util.Locale.ROOT);
        return bookingRepository.findByBookingCodeAndVehicle_Provider_Id(bookingCode, providerId);
    }
}
