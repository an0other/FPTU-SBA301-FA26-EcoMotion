package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.booking.service.BookingService;
import com.fptufa26.ecomotion.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingChargeRepository chargeRepository;
    private final PaymentTransactionRepository paymentRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Booking> listAdminBookings(BookingStatus status, String keyword) {
        List<Booking> bookings = (status != null)
                ? bookingRepository.findByStatusOrderByCreatedAtDesc(status)
                : bookingRepository.findAllByOrderByCreatedAtDesc();

        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.trim().toLowerCase(Locale.ROOT);
            return bookings.stream()
                    .filter(b -> (b.getBookingCode() != null && b.getBookingCode().toLowerCase(Locale.ROOT).contains(lower))
                            || (b.getPickupAddress() != null && b.getPickupAddress().toLowerCase(Locale.ROOT).contains(lower))
                            || (b.getVehicle() != null && (b.getVehicle().getLicensePlate().toLowerCase(Locale.ROOT).contains(lower)
                                || b.getVehicle().getModel().toLowerCase(Locale.ROOT).contains(lower))))
                    .toList();
        }
        return bookings;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Booking> getBookingById(Long bookingId) {
        return bookingRepository.findById(bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingCharge> getChargesForBooking(Long bookingId) {
        return chargeRepository.findByBooking_Id(bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransaction> listTransactions(PaymentStatus status, PaymentType type) {
        if (status != null) {
            return paymentRepository.findByStatusOrderByCreatedAtDesc(status);
        }
        if (type != null) {
            return paymentRepository.findByTypeOrderByCreatedAtDesc(type);
        }
        return paymentRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransaction> getTransactionsForBooking(Long bookingId) {
        return paymentRepository.findByBooking_IdOrderByCreatedAtDesc(bookingId);
    }

    @Override
    @Transactional
    public PaymentTransaction processMockRefund(Long bookingId, BigDecimal amount, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn thuê không tồn tại: " + bookingId));

        PaymentTransaction refund = new PaymentTransaction();
        refund.setBooking(booking);
        refund.setAmount(amount != null && amount.compareTo(BigDecimal.ZERO) > 0 ? amount : booking.getTotalAmount());
        refund.setType(PaymentType.REFUND);
        refund.setDirection(PaymentDirection.OUT);
        refund.setMethod(PaymentMethod.BANK_TRANSFER);
        refund.setStatus(PaymentStatus.PAID);
        refund.setGatewayRef("MOCK-REFUND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        refund.setPaidAt(Instant.now());
        refund.setReceiptUrl("/receipts/refund-" + booking.getBookingCode() + ".pdf");

        return paymentRepository.save(refund);
    }

    @Override
    @Transactional(readOnly = true)
    public long countBookingsByStatus(BookingStatus status) {
        return bookingRepository.countByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public long countTotalBookings() {
        return bookingRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumTotalRevenue() {
        return paymentRepository.sumTotalPaidIn();
    }
}
