package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.user.entity.Driver;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class TripTransitionsTest {
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    private Booking arrived() {
        Booking booking = new Booking();
        booking.setStatus(BookingStatus.DRIVER_ARRIVED);
        booking.setDriver(new Driver());
        booking.setActualArrivedAt(now);
        return booking;
    }

    private Booking running() {
        Booking booking = new Booking();
        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking.setActualStartAt(now);
        return booking;
    }

    @Test void driverStartMovesDirectlyToInProgressWithoutRenterConfirmation() {
        Booking b = arrived();
        TripTransitions.start(b, now);
        assertThat(b.getStatus()).isEqualTo(BookingStatus.IN_PROGRESS);
        assertThat(b.getActualStartAt()).isEqualTo(now);
        assertThat(b.getDriverStartConfirmedAt()).isEqualTo(now);
        assertThat(b.getRenterStartConfirmedAt()).isNull();
    }

    @Test void secondStartIsRejectedAndKeepsOriginalTime() {
        Booking b = arrived();
        TripTransitions.start(b, now);
        assertThatThrownBy(() -> TripTransitions.start(b, now.plusSeconds(5))).isInstanceOf(ConflictException.class);
        assertThat(b.getActualStartAt()).isEqualTo(now);
    }

    @Test void startRejectedBeforeArrivalOrAfterCancelOrEnd() {
        Booking early = new Booking();
        early.setStatus(BookingStatus.CONFIRMED);
        assertThatThrownBy(() -> TripTransitions.start(early, now)).isInstanceOf(ConflictException.class);
        Booking cancelled = arrived();
        cancelled.setCancelledAt(now);
        assertThatThrownBy(() -> TripTransitions.start(cancelled, now)).isInstanceOf(ConflictException.class);
        Booking ended = arrived();
        ended.setActualEndAt(now);
        assertThatThrownBy(() -> TripTransitions.start(ended, now)).isInstanceOf(ConflictException.class);
    }

    @Test void driverEndMovesToExtraChargeAndSetsEndTimes() {
        Booking b = running();
        TripTransitions.end(b, now.plusSeconds(7));
        assertThat(b.getStatus()).isEqualTo(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION);
        assertThat(b.getActualEndAt()).isEqualTo(now.plusSeconds(7));
        assertThat(b.getDriverEndConfirmedAt()).isEqualTo(now.plusSeconds(7));
        assertThat(b.getProviderConfirmedAt()).isEqualTo(now.plusSeconds(7));
        assertThat(b.getEndConfirmationMethod()).isEqualTo("DRIVER");
        assertThat(b.getRenterEndConfirmedAt()).isNull();
    }

    @Test void secondEndIsRejectedAndKeepsOriginalTime() {
        Booking b = running();
        TripTransitions.end(b, now);
        assertThatThrownBy(() -> TripTransitions.end(b, now.plusSeconds(9))).isInstanceOf(ConflictException.class);
        assertThat(b.getActualEndAt()).isEqualTo(now);
        assertThat(b.getDriverEndConfirmedAt()).isEqualTo(now);
    }

    @Test void endRejectedWhenNotStartedOrCancelled() {
        Booking notStarted = arrived();
        assertThatThrownBy(() -> TripTransitions.end(notStarted, now)).isInstanceOf(ConflictException.class);
        Booking cancelled = running();
        cancelled.setCancelledAt(now);
        assertThatThrownBy(() -> TripTransitions.end(cancelled, now)).isInstanceOf(ConflictException.class);
    }
}
