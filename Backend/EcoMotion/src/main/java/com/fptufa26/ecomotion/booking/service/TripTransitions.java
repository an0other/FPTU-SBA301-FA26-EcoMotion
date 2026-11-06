package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import java.time.Instant;

/**
 * Two-party start/end confirmation shared by Driver and Renter flows.
 * Call only while holding the booking's pessimistic write lock.
 *
 * <p>Rules: each side's confirmation is recorded once (a retry is a no-op and never rewrites time);
 * {@code actualStartAt}/{@code actualEndAt} are written only when the second side confirms, so the
 * order of the two confirmations does not matter.
 */
public final class TripTransitions {
    public enum Actor { DRIVER, RENTER }

    private TripTransitions() {}

    /** @return true when state changed, false when this side had already confirmed (idempotent retry). */
    public static boolean confirmStart(Booking booking, Actor actor, Instant now) {
        if (startConfirmedAt(booking, actor) != null) return false;
        if (booking.getCancelledAt() != null || booking.getStatus() == BookingStatus.CANCELLED)
            throw new ConflictException("Chuyến đi đã bị hủy");
        if (booking.getActualEndAt() != null || booking.getActualStartAt() != null)
            throw new ConflictException("Chuyến đi đã bắt đầu hoặc đã kết thúc");
        if (booking.isDriverReplacementRequired()
                || (booking.getStatus() != BookingStatus.DRIVER_ARRIVED
                && booking.getStatus() != BookingStatus.AWAITING_START_CONFIRMATION)
                || booking.getDriver() == null || booking.getActualArrivedAt() == null)
            throw new ConflictException("Chỉ có thể xác nhận bắt đầu sau khi tài xế đã đến điểm đón");
        if (actor == Actor.DRIVER) booking.setDriverStartConfirmedAt(now);
        else booking.setRenterStartConfirmedAt(now);
        if (booking.getDriverStartConfirmedAt() != null && booking.getRenterStartConfirmedAt() != null) {
            booking.setActualStartAt(now);
            booking.setStatus(BookingStatus.IN_PROGRESS);
        } else {
            booking.setStatus(BookingStatus.AWAITING_START_CONFIRMATION);
        }
        return true;
    }

    /** @return true when state changed, false when this side had already confirmed (idempotent retry). */
    public static boolean confirmEnd(Booking booking, Actor actor, Instant now) {
        if (endConfirmedAt(booking, actor) != null) return false;
        if (booking.getCancelledAt() != null || booking.getStatus() == BookingStatus.CANCELLED)
            throw new ConflictException("Chuyến đi đã bị hủy");
        if (booking.getActualEndAt() != null)
            throw new ConflictException("Chuyến đi đã kết thúc");
        if (booking.getActualStartAt() == null || (booking.getStatus() != BookingStatus.IN_PROGRESS
                && booking.getStatus() != BookingStatus.AWAITING_END_CONFIRMATION))
            throw new ConflictException("Chỉ có thể kết thúc chuyến đã bắt đầu");
        if (actor == Actor.DRIVER) {
            booking.setDriverEndConfirmedAt(now);
            if (booking.getProviderConfirmedAt() == null) booking.setProviderConfirmedAt(now);
        } else {
            booking.setRenterEndConfirmedAt(now);
            booking.setRenterConfirmedAt(now);
        }
        if (booking.getDriverEndConfirmedAt() != null && booking.getRenterEndConfirmedAt() != null) {
            booking.setActualEndAt(now);
            booking.setStatus(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION);
        } else {
            booking.setStatus(BookingStatus.AWAITING_END_CONFIRMATION);
        }
        return true;
    }

    private static Instant startConfirmedAt(Booking b, Actor a) {
        return a == Actor.DRIVER ? b.getDriverStartConfirmedAt() : b.getRenterStartConfirmedAt();
    }

    private static Instant endConfirmedAt(Booking b, Actor a) {
        return a == Actor.DRIVER ? b.getDriverEndConfirmedAt() : b.getRenterEndConfirmedAt();
    }
}
