package com.fptufa26.ecomotion.booking.policy;

import com.fptufa26.ecomotion.common.exception.BadRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BookingStartPolicy {
    private final Clock clock;
    private final int maxAdvanceMonths;

    public BookingStartPolicy(Clock clock, @Value("${booking.max-advance-months}") int maxAdvanceMonths) {
        if (maxAdvanceMonths < 1) throw new IllegalArgumentException("booking.max-advance-months must be positive");
        this.clock = clock;
        this.maxAdvanceMonths = maxAdvanceMonths;
    }

    public void validate(Instant pickupTime) {
        Instant latest = clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .plusMonths(maxAdvanceMonths).toInstant();
        if (pickupTime != null && pickupTime.isAfter(latest)) {
            throw new BadRequestException("Chỉ được đặt giờ nhận xe trong vòng " + maxAdvanceMonths + " tháng kể từ hiện tại");
        }
    }
}
