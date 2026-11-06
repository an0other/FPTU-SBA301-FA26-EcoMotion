package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.service.TripTransitions.Actor;
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

    @Test void driverStartsFirstThenRenterStartsAndOnlySecondConfirmationSetsActualStart() {
        Booking b = arrived();
        assertThat(TripTransitions.confirmStart(b, Actor.DRIVER, now)).isTrue();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.AWAITING_START_CONFIRMATION);
        assertThat(b.getActualStartAt()).isNull();
        assertThat(TripTransitions.confirmStart(b, Actor.DRIVER, now.plusSeconds(5))).isFalse();
        assertThat(TripTransitions.confirmStart(b, Actor.RENTER, now.plusSeconds(10))).isTrue();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.IN_PROGRESS);
        assertThat(b.getActualStartAt()).isEqualTo(now.plusSeconds(10));
        assertThat(TripTransitions.confirmStart(b, Actor.RENTER, now.plusSeconds(20))).isFalse();
        assertThat(b.getActualStartAt()).isEqualTo(now.plusSeconds(10));
    }

    @Test void renterStartsFirstIsHandledSymmetrically() {
        Booking b = arrived();
        assertThat(TripTransitions.confirmStart(b, Actor.RENTER, now)).isTrue();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.AWAITING_START_CONFIRMATION);
        assertThat(TripTransitions.confirmStart(b, Actor.DRIVER, now.plusSeconds(3))).isTrue();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.IN_PROGRESS);
        assertThat(b.getActualStartAt()).isEqualTo(now.plusSeconds(3));
    }

    @Test void startRejectedBeforeArrivalOrAfterCancelOrEnd() {
        Booking early = new Booking();
        early.setStatus(BookingStatus.CONFIRMED);
        assertThatThrownBy(() -> TripTransitions.confirmStart(early, Actor.DRIVER, now)).isInstanceOf(ConflictException.class);
        Booking cancelled = arrived();
        cancelled.setCancelledAt(now);
        assertThatThrownBy(() -> TripTransitions.confirmStart(cancelled, Actor.RENTER, now)).isInstanceOf(ConflictException.class);
        Booking ended = arrived();
        ended.setActualEndAt(now);
        assertThatThrownBy(() -> TripTransitions.confirmStart(ended, Actor.RENTER, now)).isInstanceOf(ConflictException.class);
    }

    @Test void endInEitherOrderSetsActualEndOnceOnSecondConfirmation() {
        Booking driverFirst = running();
        assertThat(TripTransitions.confirmEnd(driverFirst, Actor.DRIVER, now)).isTrue();
        assertThat(driverFirst.getStatus()).isEqualTo(BookingStatus.AWAITING_END_CONFIRMATION);
        assertThat(driverFirst.getActualEndAt()).isNull();
        assertThat(driverFirst.getProviderConfirmedAt()).isEqualTo(now);
        assertThat(TripTransitions.confirmEnd(driverFirst, Actor.RENTER, now.plusSeconds(7))).isTrue();
        assertThat(driverFirst.getActualEndAt()).isEqualTo(now.plusSeconds(7));
        assertThat(driverFirst.getStatus()).isEqualTo(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION);

        Booking renterFirst = running();
        assertThat(TripTransitions.confirmEnd(renterFirst, Actor.RENTER, now)).isTrue();
        assertThat(renterFirst.getStatus()).isEqualTo(BookingStatus.AWAITING_END_CONFIRMATION);
        assertThat(TripTransitions.confirmEnd(renterFirst, Actor.DRIVER, now.plusSeconds(4))).isTrue();
        assertThat(renterFirst.getActualEndAt()).isEqualTo(now.plusSeconds(4));
    }

    @Test void endRetriesNeverRewriteTimeOrFail() {
        Booking b = running();
        TripTransitions.confirmEnd(b, Actor.RENTER, now);
        assertThat(TripTransitions.confirmEnd(b, Actor.RENTER, now.plusSeconds(9))).isFalse();
        assertThat(b.getRenterEndConfirmedAt()).isEqualTo(now);
        TripTransitions.confirmEnd(b, Actor.DRIVER, now.plusSeconds(10));
        assertThat(TripTransitions.confirmEnd(b, Actor.RENTER, now.plusSeconds(20))).isFalse();
        assertThat(TripTransitions.confirmEnd(b, Actor.DRIVER, now.plusSeconds(30))).isFalse();
        assertThat(b.getActualEndAt()).isEqualTo(now.plusSeconds(10));
    }

    @Test void endRejectedBeforeStartOrWhenCancelled() {
        Booking notStarted = new Booking();
        notStarted.setStatus(BookingStatus.CONFIRMED);
        assertThatThrownBy(() -> TripTransitions.confirmEnd(notStarted, Actor.RENTER, now)).isInstanceOf(ConflictException.class);
        Booking cancelled = running();
        cancelled.setStatus(BookingStatus.CANCELLED);
        assertThatThrownBy(() -> TripTransitions.confirmEnd(cancelled, Actor.DRIVER, now)).isInstanceOf(ConflictException.class);
    }
}
