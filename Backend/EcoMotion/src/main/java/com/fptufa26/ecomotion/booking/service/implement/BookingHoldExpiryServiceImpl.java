package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.CancelReason;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.service.BookingHoldExpiryService;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class BookingHoldExpiryServiceImpl implements BookingHoldExpiryService {

    private final BookingRepository bookingRepository;
    private final ScheduleService scheduleService;
    private final PaymentRequestService paymentRequestService;

    @Override
    @Transactional
    public void expireDueHolds(Instant now) {
        for (Long bookingId : scheduleService.expiredHoldBookingIds(now)) {
            Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
            if (booking == null || booking.getStatus() != BookingStatus.AWAITING_DEPOSIT) {
                continue;
            }
            if (!paymentRequestService.expireDepositWithoutProof(bookingId)) {
                continue;
            }
            scheduleService.release(bookingId);
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancelReason(CancelReason.DEPOSIT_TIMEOUT);
            booking.setCancelledAt(now);
        }
    }
}
