package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import java.time.Instant;

/**
 * Driver-only trip start/end transitions. Renter does not confirm start or end.
 * Call only while holding the booking's pessimistic write lock, and only after the caller has
 * verified that the driver is assigned to the booking.
 *
 * <p>Each transition is one-way: a second start or a second end is rejected with a {@link ConflictException}
 * instead of silently rewriting timestamps.
 */
public final class TripTransitions {

    private TripTransitions() {}

    /** DRIVER_ARRIVED -> IN_PROGRESS. Sets driverStartConfirmedAt and actualStartAt to {@code now}. */
    public static void start(Booking booking, Instant now) {
        if (booking.getCancelledAt() != null || booking.getStatus() == BookingStatus.CANCELLED)
            throw new ConflictException("Chuyến đi đã bị hủy");
        if (booking.getActualStartAt() != null || booking.getStatus() == BookingStatus.IN_PROGRESS)
            throw new ConflictException("Chuyến đi đã bắt đầu");
        if (booking.getActualEndAt() != null)
            throw new ConflictException("Chuyến đi đã kết thúc");
        if (booking.isDriverReplacementRequired()
                || booking.getStatus() != BookingStatus.DRIVER_ARRIVED
                || booking.getDriver() == null || booking.getActualArrivedAt() == null)
            throw new ConflictException("Chỉ có thể bắt đầu chuyến sau khi tài xế đã đến điểm đón");
        booking.setDriverStartConfirmedAt(now);
        booking.setActualStartAt(now);
        booking.setStatus(BookingStatus.IN_PROGRESS);
    }

    /**
     * IN_PROGRESS -> AWAITING_EXTRA_CHARGE_CONFIRMATION (the provider then settles extra charges).
     * Sets driverEndConfirmedAt, actualEndAt and providerConfirmedAt (when not yet set) to {@code now}.
     */
    public static void end(Booking booking, Instant now) {
        if (booking.getCancelledAt() != null || booking.getStatus() == BookingStatus.CANCELLED)
            throw new ConflictException("Chuyến đi đã bị hủy");
        if (booking.getActualEndAt() != null || booking.getDriverEndConfirmedAt() != null)
            throw new ConflictException("Chuyến đi đã kết thúc");
        if (booking.getActualStartAt() == null || booking.getStatus() != BookingStatus.IN_PROGRESS)
            throw new ConflictException("Chỉ có thể kết thúc chuyến đang diễn ra");
        booking.setDriverEndConfirmedAt(now);
        booking.setActualEndAt(now);
        booking.setEndConfirmationMethod("DRIVER");
        if (booking.getProviderConfirmedAt() == null) booking.setProviderConfirmedAt(now);
        booking.setStatus(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION);
    }
}
