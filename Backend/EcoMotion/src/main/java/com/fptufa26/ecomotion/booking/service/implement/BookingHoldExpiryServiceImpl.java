package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.CancelReason;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.service.BookingHoldExpiryService;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestType;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;

import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import com.fptufa26.ecomotion.booking.service.BookingNotificationService;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;

@Service
@RequiredArgsConstructor
public class BookingHoldExpiryServiceImpl implements BookingHoldExpiryService {

    private final BookingRepository bookingRepository;
    private final ScheduleService scheduleService;
    private final PaymentRequestService paymentRequestService;
    private final PaymentTransactionRepository transactions;
    private final BookingNotificationService notifications;

    /** How long a PENDING VNPAY payment may hold the deposit open before the hold expires. */
    @org.springframework.beans.factory.annotation.Value("${app.vnpay.pending-grace:PT30M}")
    private java.time.Duration vnpayPendingGrace;

    @Override
    @Transactional
    public void expireDueHolds(Instant now) {
        for (Long bookingId : scheduleService.expiredHoldBookingIds(now)) {
            Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
            if (booking == null) {
                continue;
            }
            if (booking.getPlan() != null && booking.getPlan().getPlanType() == PlanType.YEARLY
                    && booking.getStatus() == BookingStatus.PENDING) {
                expireYearly(booking, now, CancelReason.YEARLY_REQUEST_TIMEOUT);
                continue;
            }
            if (booking.getPlan() != null && booking.getPlan().getPlanType() == PlanType.YEARLY
                    && booking.getStatus() == BookingStatus.ACCEPTED) {
                expireYearly(booking, now, CancelReason.YEARLY_CONTRACT_TIMEOUT);
                continue;
            }
            if (booking.getStatus() != BookingStatus.AWAITING_DEPOSIT) {
                continue;
            }
            Optional<PaymentRequest> depositRequest = paymentRequestService
                    .findPaymentRequest(bookingId, PaymentRequestType.DEPOSIT);
            if (depositRequest.isPresent() && Set.of(
                    PaymentRequestStatus.PROOF_SUBMITTED,
                    PaymentRequestStatus.PARTIALLY_PAID,
                    PaymentRequestStatus.REVIEW_REQUIRED).contains(depositRequest.get().getStatus())) {
                continue;
            }
            Instant pendingCutoff = now.minus(vnpayPendingGrace);
            if (transactions.findByBooking_Id(bookingId).stream().anyMatch(t ->
                    t.getMethod() == PaymentMethod.VNPAY
                            && t.getStatus() == PaymentStatus.PENDING
                            && t.getCreatedAt() != null && t.getCreatedAt().isAfter(pendingCutoff))) continue;
            if (!paymentRequestService.expireDepositWithoutProof(bookingId)) {
                continue;
            }
            scheduleService.release(bookingId);
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancelReason(CancelReason.DEPOSIT_TIMEOUT);
            booking.setCancelledAt(now);
            notifications.changed(booking, "BOOKING_CHANGED");
        }
    }

    private void expireYearly(Booking booking, Instant now, CancelReason reason) {
        scheduleService.release(booking.getId());
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelReason(reason);
        booking.setCancelledAt(now);
        booking.setCancelledBy(null);
        notifications.changed(booking, "BOOKING_CHANGED");
    }
}
