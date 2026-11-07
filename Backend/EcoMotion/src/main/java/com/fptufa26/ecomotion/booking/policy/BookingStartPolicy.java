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

    public BookingStartPolicy(Clock clock, @Value("${booking.max-advance-months:3}") int maxAdvanceMonths) {
        if (maxAdvanceMonths < 1) throw new IllegalArgumentException("booking.max-advance-months must be positive");
        this.clock = clock;
        this.maxAdvanceMonths = maxAdvanceMonths;
    }

    public record StartWindow(int maxAdvanceMonths, Instant latestPickupTime) {}

    public StartWindow window() {
        Instant latest = clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .plusMonths(maxAdvanceMonths).toInstant();
        return new StartWindow(maxAdvanceMonths, latest);
    }

    public void validate(Instant pickupTime) {
        if (pickupTime != null && pickupTime.isAfter(window().latestPickupTime())) {
            throw new BadRequestException("Chỉ được đặt giờ nhận xe trong vòng " + maxAdvanceMonths + " tháng kể từ hiện tại");
        }
    }
}
