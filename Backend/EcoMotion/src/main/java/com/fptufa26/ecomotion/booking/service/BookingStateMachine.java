package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.exception.InvalidTripTransitionException;
import com.fptufa26.ecomotion.booking.exception.TripConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Centralized State Machine and Transition Rules for Booking / Trip lifecycle.
 * Enforces strict consistency and prevents invalid or corrupt state transitions.
 */
public final class BookingStateMachine {

    private static final Logger log = LoggerFactory.getLogger(BookingStateMachine.class);

    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(BookingStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(BookingStatus.PENDING, Set.of(BookingStatus.ACCEPTED, BookingStatus.REJECTED, BookingStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BookingStatus.ACCEPTED, Set.of(BookingStatus.CONFIRMED, BookingStatus.DRIVER_ASSIGNED, BookingStatus.CANCELLED, BookingStatus.REJECTED));
        ALLOWED_TRANSITIONS.put(BookingStatus.CONFIRMED, Set.of(BookingStatus.DRIVER_ASSIGNED, BookingStatus.DRIVER_CONFIRMED, BookingStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BookingStatus.DRIVER_ASSIGNED, Set.of(BookingStatus.DRIVER_CONFIRMED, BookingStatus.DRIVER_ASSIGNED, BookingStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BookingStatus.DRIVER_CONFIRMED, Set.of(BookingStatus.DRIVER_ARRIVED, BookingStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BookingStatus.DRIVER_ARRIVED, Set.of(BookingStatus.IN_PROGRESS, BookingStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BookingStatus.IN_PROGRESS, Set.of(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION, BookingStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION, Set.of(BookingStatus.AWAITING_FINAL_PAYMENT, BookingStatus.COMPLETED));
        ALLOWED_TRANSITIONS.put(BookingStatus.AWAITING_FINAL_PAYMENT, Set.of(BookingStatus.COMPLETED));
        ALLOWED_TRANSITIONS.put(BookingStatus.COMPLETED, Set.of());
        ALLOWED_TRANSITIONS.put(BookingStatus.CANCELLED, Set.of());
        ALLOWED_TRANSITIONS.put(BookingStatus.REJECTED, Set.of());
    }

    private BookingStateMachine() {
    }

    /**
     * Validates and applies transition from current status to target status.
     * Throws appropriate 409 Conflict exceptions if invalid or duplicate.
     */
    public static void validateTransition(Booking booking, BookingStatus targetStatus) {
        BookingStatus current = booking.getStatus();

        if (current == targetStatus) {
            // Re-assigning same driver is allowed as an idempotent update
            if (targetStatus == BookingStatus.DRIVER_ASSIGNED) {
                return;
            }
            throw new TripConflictException("Chuyến đi/Đơn thuê đã ở trạng thái: " + targetStatus);
        }

        if (current == BookingStatus.CANCELLED || booking.getCancelledAt() != null) {
            throw new InvalidTripTransitionException("Không thể thao tác trên chuyến đi đã bị hủy");
        }
        if (current == BookingStatus.REJECTED) {
            throw new InvalidTripTransitionException("Không thể thao tác trên chuyến đi đã bị từ chối");
        }
        if (current == BookingStatus.COMPLETED) {
            throw new InvalidTripTransitionException("Không thể thao tác trên chuyến đi đã hoàn thành");
        }

        Set<BookingStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(targetStatus)) {
            log.error("Invalid state transition attempted: {} -> {} for booking {}", current, targetStatus, booking.getBookingCode());
            throw new InvalidTripTransitionException(String.format(
                    "Chuyển đổi trạng thái không hợp lệ: %s -> %s. Các trạng thái cho phép: %s",
                    current, targetStatus, allowed
            ));
        }
    }
}
