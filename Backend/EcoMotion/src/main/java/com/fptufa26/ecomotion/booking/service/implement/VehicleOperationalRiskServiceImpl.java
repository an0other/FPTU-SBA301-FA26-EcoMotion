package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.dto.response.OperationalRiskResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.exception.BookingNotFoundException;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.service.VehicleOperationalRiskService;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.Comparator;
import org.springframework.security.access.AccessDeniedException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleOperationalRiskServiceImpl implements VehicleOperationalRiskService {

    private static final Set<BookingStatus> VEHICLE_NOT_RETURNED_STATUSES = EnumSet.of(
            BookingStatus.IN_PROGRESS,
            BookingStatus.AWAITING_END_CONFIRMATION,
            BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION,
            BookingStatus.AWAITING_FINAL_PAYMENT
    );

    private static final Set<BookingStatus> COMMITTED_STATUSES = EnumSet.of(
            BookingStatus.CONFIRMED,
            BookingStatus.DRIVER_ASSIGNED,
            BookingStatus.DRIVER_CONFIRMED,
            BookingStatus.DRIVER_ARRIVED,
            BookingStatus.AWAITING_START_CONFIRMATION,
            BookingStatus.IN_PROGRESS,
            BookingStatus.AWAITING_END_CONFIRMATION
    );

    private final BookingRepository bookingRepository;
    private final UserService userService;
    private final Clock clock;
    private final ScheduleService scheduleService;

    @Override
    public OperationalRiskResponse assess(Booking booking) {
        if (booking != null && (booking.getCancelledAt() != null || EnumSet.of(BookingStatus.CANCELLED,
                BookingStatus.CANCELLATION_REVIEW, BookingStatus.REJECTED, BookingStatus.COMPLETED).contains(booking.getStatus())))
            return OperationalRiskResponse.safe();
        if (booking == null || booking.getId() == null || booking.getVehicle() == null
                || booking.getVehicle().getId() == null || booking.getPickupTime() == null) {
            return OperationalRiskResponse.safe();
        }
        return bookingRepository.findByVehicle_IdAndStatusIn(
                        booking.getVehicle().getId(), VEHICLE_NOT_RETURNED_STATUSES).stream()
                .filter(other -> !booking.getId().equals(other.getId()))
                .filter(other -> other.getPickupTime() != null
                        && other.getPickupTime().isBefore(booking.getPickupTime()))
                .filter(other -> other.getActualEndAt() == null)
                .min(Comparator.comparing(Booking::getPickupTime).reversed())
                .map(other -> lateness(booking, other))
                .orElseGet(OperationalRiskResponse::safe);
    }

    private OperationalRiskResponse lateness(Booking booking, Booking previous) {
        Instant plannedEnd = previous.getPlannedEndAt();
        if (plannedEnd == null) return OperationalRiskResponse.vehicleBusy();
        Instant now = clock.instant();
        boolean overdue = now.isAfter(plannedEnd);
        int bufferMinutes = booking.getVehicle().getMaintenanceBufferMinutes() == null
                ? 0 : Math.max(0, booking.getVehicle().getMaintenanceBufferMinutes());
        Instant readyAt = (overdue ? now : plannedEnd).plus(Duration.ofMinutes(bufferMinutes));
        if (!readyAt.isAfter(booking.getPickupTime())) return OperationalRiskResponse.safe();
        long delayMinutes = Math.max(1, Duration.between(booking.getPickupTime(), readyAt).toMinutes());
        return OperationalRiskResponse.vehicleLate(previous.getBookingCode(), plannedEnd, readyAt, delayMinutes, overdue);
    }

    @Override
    public OperationalRiskResponse assessForRenter(Long userId, String bookingCode) {
        String normalized = normalize(bookingCode);
        Booking booking = bookingRepository.findByBookingCodeAndRenter_User_Id(normalized, userId)
                .orElseThrow(() -> new BookingNotFoundException(normalized));
        return assess(booking);
    }

    @Override
    public OperationalRiskResponse assessForProvider(Long userId, String bookingCode) {
        Long providerId = userService.findProviderByUserId(userId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Tài khoản không thuộc provider"))
                .getId();
        String normalized = normalize(bookingCode);
        Booking booking = bookingRepository.findByBookingCodeAndVehicle_Provider_Id(normalized, providerId)
                .orElseThrow(() -> new BookingNotFoundException(normalized));
        return assess(booking);
    }

    @Override
    public void requireVehicleReadyForWindow(Booking booking, Vehicle vehicle, Instant start, Instant end) {
        Long selfId = booking.getId();
        // 1. A vehicle still out on an earlier trip is not free, even if its schedule row has already expired.
        boolean notReturned = bookingRepository.findByVehicle_IdAndStatusIn(vehicle.getId(), VEHICLE_NOT_RETURNED_STATUSES).stream()
                .filter(other -> !other.getId().equals(selfId))
                .anyMatch(other -> other.getActualEndAt() == null && other.getPickupTime() != null
                        && other.getPickupTime().isBefore(start));
        if (notReturned) {
            throw new ConflictException("Xe đang chạy chuyến trước và chưa được trả về; không thể nhận chuyến này vào giờ đã chọn");
        }
        // 2. Another committed booking on the same vehicle that overlaps the window (does not rely on a schedule row existing).
        boolean overlaps = bookingRepository.findByVehicle_IdAndStatusIn(vehicle.getId(), COMMITTED_STATUSES).stream()
                .filter(other -> !other.getId().equals(selfId))
                .anyMatch(other -> other.getPickupTime() != null && other.getPlannedEndAt() != null
                        && other.getPickupTime().isBefore(end) && other.getPlannedEndAt().isAfter(start));
        if (overlaps) {
            throw new ConflictException("Xe đã có chuyến khác trong khoảng thời gian thuê này");
        }
        // 3. Schedule table (holds, maintenance, blocks, buffers, yearly custody).
        scheduleService.assertFreeForMove(booking, vehicle, start, end);
    }

    private String normalize(String bookingCode) {
        return bookingCode == null ? "" : bookingCode.trim().toUpperCase(Locale.ROOT);
    }
}
