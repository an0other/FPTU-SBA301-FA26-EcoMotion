package com.fptufa26.ecomotion.booking.policy;

import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BookingStartPolicyTest {
    @Test void usesCalendarMonthsAndAllowsExactBoundary() {
        Instant now = ZonedDateTime.of(2026, 1, 31, 10, 0, 0, 0, ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        var policy = new BookingStartPolicy(Clock.fixed(now, ZoneOffset.UTC), 1);
        Instant limit = now.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).plusMonths(1).toInstant();
        assertThatCode(() -> policy.validate(limit)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validate(limit.plusSeconds(1))).hasMessageContaining("1 tháng");
        assertThatCode(() -> new BookingStartPolicy(Clock.fixed(now, ZoneOffset.UTC), 2).validate(limit.plusSeconds(1))).doesNotThrowAnyException();
    }
    @Test void rejectsInvalidConfiguration() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BookingStartPolicy(Clock.systemUTC(), 0));
    }
}
