package com.fptufa26.ecomotion.booking.job;

import com.fptufa26.ecomotion.booking.service.BookingHoldExpiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@RequiredArgsConstructor
public class BookingHoldExpiryJob {

    private final BookingHoldExpiryService bookingHoldExpiryService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${ecomotion.booking-hold.cleanup-ms:60000}")
    public void expireDueHolds() {
        bookingHoldExpiryService.expireDueHolds(clock.instant());
    }
}
