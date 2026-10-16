package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.dto.response.BookingChargeResponse;
import com.fptufa26.ecomotion.booking.dto.response.BookingChargesResponse;
import com.fptufa26.ecomotion.booking.dto.response.PaymentHistoryResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.exception.BookingNotFoundException;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.booking.service.RenterPaymentService;
import com.fptufa26.ecomotion.common.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RenterPaymentServiceImpl implements RenterPaymentService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final BookingRepository bookingRepository;
    private final BookingChargeRepository bookingChargeRepository;

    @Override
    public PageResponse<PaymentHistoryResponse> getPaymentHistory(
            Long userId,
            int page,
            int size) {
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException("Invalid pagination parameters");
        }

        Pageable pageable = PageRequest.of(page, size);
        int offset = Math.toIntExact(pageable.getOffset());

        Page<PaymentTransaction> paymentPage = paymentTransactionRepository
                .findByBooking_Renter_User_IdAndTypeNotOrderByCreatedAtDescIdDesc(
                        userId,
                        PaymentType.PAYOUT,
                        pageable);

        List<PaymentHistoryResponse> items = paymentPage.getContent()
                .stream()
                .map(this::toPaymentHistoryResponse)
                .toList();

        return new PageResponse<>(
                items,
                paymentPage.getTotalElements(),
                offset,
                size,
                paymentPage.hasNext());
    }

    @Override
    public BookingChargesResponse getBookingCharges(
            Long userId,
            String bookingCode) {
        Booking booking = bookingRepository
                .findByBookingCodeAndRenter_User_Id(bookingCode, userId)
                .orElseThrow(BookingNotFoundException::new);

        List<BookingChargeResponse> charges = bookingChargeRepository
                .findByBooking_IdOrderByCreatedAtAscIdAsc(booking.getId())
                .stream()
                .map(this::toBookingChargeResponse)
                .toList();

        return new BookingChargesResponse(
                booking.getBookingCode(),
                booking.getStatus(),
                charges);
    }

    private PaymentHistoryResponse toPaymentHistoryResponse(
            PaymentTransaction payment) {
        return new PaymentHistoryResponse(
                payment.getBooking().getBookingCode(),
                payment.getType(),
                payment.getAmount(),
                payment.getDirection(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getReceiptUrl(),
                payment.getPaidAt(),
                payment.getCreatedAt());
    }

    private BookingChargeResponse toBookingChargeResponse(
            BookingCharge charge) {
        return new BookingChargeResponse(
                charge.getChargeType(),
                charge.getDescription(),
                charge.getQuantity(),
                charge.getUnitPrice(),
                charge.getAppliedRate(),
                charge.getAmount(),
                charge.isWaived(),
                charge.getEvidenceUrl(),
                charge.getCreatedAt());
    }
}