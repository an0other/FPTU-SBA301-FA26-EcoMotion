package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.dto.response.BookingChargeResponse;
import com.fptufa26.ecomotion.booking.dto.response.BookingChargesResponse;
import com.fptufa26.ecomotion.booking.dto.response.PaymentHistoryResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.exception.BookingNotFoundException;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
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
import com.fptufa26.ecomotion.booking.dto.response.BookingPaymentSummaryResponse;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RenterPaymentServiceImpl implements RenterPaymentService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final BookingRepository bookingRepository;
    private final BookingChargeRepository bookingChargeRepository;
    private final BookingPolicySnapshotRepository snapshotRepository;

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
                .orElseThrow(() -> new BookingNotFoundException(
                        "Không tìm thấy đơn thuê: " + bookingCode));


        BookingPolicySnapshot snapshot = snapshotRepository
                .findByBooking_Id(booking.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Booking policy snapshot is missing"));

        List<BookingCharge> charges = bookingChargeRepository
                .findByBooking_IdOrderByCreatedAtAscIdAsc(booking.getId());

        List<PaymentTransaction> transactions = paymentTransactionRepository
                .findByBooking_IdAndTypeNotOrderByCreatedAtDescIdDesc(
                        booking.getId(),
                        PaymentType.PAYOUT);

        return new BookingChargesResponse(
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getVehicle().getBrand()
                        + " "
                        + booking.getVehicle().getModel(),
                snapshot.getPlanType(),
                buildPaymentSummary(booking, snapshot, charges, transactions),
                charges.stream()
                        .map(this::toBookingChargeResponse)
                        .toList(),
                transactions.stream()
                        .map(this::toPaymentHistoryResponse)
                        .toList());
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
                charge.getId(),
                charge.getChargeType(),
                charge.getDescription(),
                charge.getQuantity(),
                charge.getUnitPrice(),
                charge.getAppliedRate(),
                charge.getAmount(),
                charge.isWaived(),
                charge.getEvidenceUrl(),
                charge.getCreatedBy() == null
                        ? null
                        : charge.getCreatedBy().getFullName(),
                charge.getCreatedAt());
    }

    private BookingPaymentSummaryResponse buildPaymentSummary(
            Booking booking,
            BookingPolicySnapshot snapshot,
            List<BookingCharge> charges,
            List<PaymentTransaction> transactions) {
        boolean yearly = snapshot.getPlanType() == PlanType.YEARLY;
        boolean closedWithoutCompletion =
                booking.getStatus() == BookingStatus.CANCELLED
                        || booking.getStatus() == BookingStatus.REJECTED;

        boolean hasBaseRental = charges.stream()
                .anyMatch(charge ->
                        charge.getChargeType() == ChargeType.BASE_RENTAL);

        BigDecimal recordedCost = hasBaseRental
                ? charges.stream()
                .filter(charge -> !charge.isWaived())
                .filter(charge ->
                        charge.getChargeType() != ChargeType.DEPOSIT)
                .map(BookingCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                : null;

        BigDecimal depositRequired = yearly
                ? null
                : charges.stream()
                .filter(charge ->
                        charge.getChargeType() == ChargeType.DEPOSIT)
                .map(charge -> charge.isWaived()
                        ? BigDecimal.ZERO
                        : charge.getAmount())
                .reduce(BigDecimal::add)
                .orElse(null);

        BigDecimal paidAmount = transactions.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.PAID)
                .filter(payment ->
                        payment.getType() == PaymentType.DEPOSIT_PAY
                                || payment.getType() == PaymentType.UPFRONT_PAY
                                || payment.getType() == PaymentType.SETTLEMENT)
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal allocatedPayments = transactions.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.PAID)
                .filter(payment -> payment.getDirection() == com.fptufa26.ecomotion.booking.enums.PaymentDirection.IN)
                .map(payment -> payment.getAppliedAmount() != null && payment.getAppliedAmount().signum() > 0
                        ? payment.getAppliedAmount()
                        : payment.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal refundedAmount = transactions.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.PAID)
                .filter(payment -> payment.getType() == PaymentType.REFUND)
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netPaidAmount = paidAmount.subtract(refundedAmount).max(BigDecimal.ZERO);

        boolean canCalculateBalance =
                !yearly
                        && !closedWithoutCompletion
                        && recordedCost != null;

        BigDecimal remainingAmount = canCalculateBalance
                ? recordedCost.subtract(allocatedPayments).max(BigDecimal.ZERO)
                : null;

        BigDecimal creditAmount = canCalculateBalance
                ? netPaidAmount.subtract(recordedCost).max(BigDecimal.ZERO)
                : null;

        boolean provisional =
                booking.getStatus() != BookingStatus.COMPLETED;

        String balanceNote;

        if (closedWithoutCompletion) {
            balanceNote =
                    "Đơn đã hủy hoặc bị từ chối. Các giao dịch hoàn tiền được hiển thị riêng; không suy ra dư nợ tiền thuê.";
        } else if (yearly) {
            balanceNote =
                    "Gói năm có khoản trả trước và nghĩa vụ trả góp theo hợp đồng; chưa tổng hợp dư nợ trả góp tại đây.";
        } else if (!hasBaseRental) {
            balanceNote =
                    "Chưa ghi nhận khoản tiền thuê gốc nên chưa xác định số tiền còn lại.";
        } else if (provisional) {
            balanceNote =
                    "Số còn lại dự tính theo chi phí và giao dịch đã ghi nhận; có thể thay đổi khi phát sinh hoặc miễn phụ phí.";
        } else {
            balanceNote =
                    "Số còn lại theo chi phí và giao dịch đã ghi nhận. Khoản trả vượt chưa đồng nghĩa đã có quyết định hoàn tiền.";
        }

        return new BookingPaymentSummaryResponse(
                recordedCost,
                depositRequired,
                yearly ? snapshot.getUpfrontAmount() : null,
                paidAmount,
                refundedAmount,
                netPaidAmount,
                remainingAmount,
                creditAmount,
                provisional,
                balanceNote);
    }
}
