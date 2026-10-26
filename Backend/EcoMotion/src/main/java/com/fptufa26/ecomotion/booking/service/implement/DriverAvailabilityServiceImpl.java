package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.service.AvailabilityPolicy;
import com.fptufa26.ecomotion.booking.service.DriverAvailabilityService;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.service.DriverAssignmentService;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DriverAvailabilityServiceImpl implements DriverAvailabilityService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<BookingStatus> OCCUPYING_STATUSES = List.of(
            BookingStatus.CONFIRMED,
            BookingStatus.DRIVER_ASSIGNED,
            BookingStatus.DRIVER_CONFIRMED,
            BookingStatus.DRIVER_ARRIVED,
            BookingStatus.AWAITING_START_CONFIRMATION,
            BookingStatus.IN_PROGRESS,
            BookingStatus.AWAITING_END_CONFIRMATION);

    private final BookingRepository bookingRepository;
    private final BookingPolicySnapshotRepository snapshotRepository;
    private final BookingChargeRepository chargeRepository;
    private final DriverAssignmentService driverAssignmentService;

    @Override
    public List<Driver> findAvailable(Long providerId, Vehicle vehicle, Instant start, Instant end,
                                      Long excludeBookingId) {
        return driverAssignmentService.listEligible(
                        providerId, start.atZone(VIETNAM_ZONE).toLocalDate()).stream()
                .filter(driver -> isAvailable(driver.getId(), vehicle, start, end, excludeBookingId))
                .toList();
    }

    @Override
    public boolean isAvailable(Long driverId, Vehicle vehicle, Instant start, Instant end,
                               Long excludeBookingId) {
        AvailabilityPolicy.Window targetWindow = AvailabilityPolicy.bufferedWindow(vehicle, start, end);
        return bookingRepository.findPotentialDriverConflicts(
                        driverId, OCCUPYING_STATUSES, excludeBookingId, targetWindow.end()).stream()
                .noneMatch(existing -> overlaps(existing, targetWindow));
    }

    private boolean overlaps(Booking existing, AvailabilityPolicy.Window targetWindow) {
        if ((existing.getStatus() == BookingStatus.IN_PROGRESS
                || existing.getStatus() == BookingStatus.AWAITING_END_CONFIRMATION)
                && existing.getActualEndAt() == null) {
            return true;
        }
        AvailabilityPolicy.Window existingWindow = AvailabilityPolicy.bufferedWindow(
                existing.getVehicle(), existing.getPickupTime(), plannedEnd(existing));
        return existingWindow.start().isBefore(targetWindow.end())
                && existingWindow.end().isAfter(targetWindow.start());
    }

    private Instant plannedEnd(Booking booking) {
        BookingPolicySnapshot snapshot = snapshotRepository.findByBooking_Id(booking.getId()).orElse(null);
        int slotLength = snapshot != null
                ? snapshot.getSlotLengthMinutes()
                : booking.getPlan().getSlotLengthMinutes();
        int quantity = chargeRepository.findByBooking_IdAndChargeType(booking.getId(), ChargeType.BASE_RENTAL)
                .map(BookingCharge::getQuantity)
                .orElse(1);
        return booking.getPickupTime().plus(Duration.ofMinutes((long) slotLength * Math.max(1, quantity)));
    }
}
