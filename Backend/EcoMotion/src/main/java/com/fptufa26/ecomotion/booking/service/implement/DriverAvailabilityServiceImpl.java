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
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;
import java.util.Objects;
import com.fptufa26.ecomotion.booking.dto.response.AssignmentAvailabilityResponse;
import com.fptufa26.ecomotion.user.enums.DriverActivityStatus;
import java.time.Clock;

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
    private final Clock clock;

    @Override
    public Map<Long, AssignmentAvailabilityResponse> inspect(Long providerId, List<Driver> drivers,
            Vehicle vehicle, Instant start, Instant end, Long excludeBookingId) {
        Instant now = clock.instant();
        List<Booking> schedule = bookingRepository.findDriverScheduleForProvider(providerId, OCCUPYING_STATUSES)
                .stream().filter(b -> holdsSlot(b, now)).toList();
        List<Long> ids = schedule.stream().map(Booking::getId).toList();
        Map<Long, Integer> lengths = new HashMap<>();
        Map<Long, Integer> quantities = new HashMap<>();
        if (!ids.isEmpty()) {
            snapshotRepository.findByBooking_IdIn(ids).forEach(s -> lengths.put(s.getBooking().getId(), s.getSlotLengthMinutes()));
            chargeRepository.findByBooking_IdInAndChargeType(ids, ChargeType.BASE_RENTAL)
                    .forEach(c -> quantities.put(c.getBooking().getId(), c.getQuantity()));
        }
        Map<Long, Instant> ends = new HashMap<>();
        for (Booking b : schedule) {
            int minutes = lengths.getOrDefault(b.getId(), b.getPlan() == null ? 60 : b.getPlan().getSlotLengthMinutes());
            ends.put(b.getId(), b.getPickupTime().plus(Duration.ofMinutes((long) minutes * Math.max(1, quantities.getOrDefault(b.getId(), 1)))));
        }
        Map<Long, AssignmentAvailabilityResponse> result = new HashMap<>();
        for (Driver driver : drivers) {
            List<Booking> assigned = schedule.stream().filter(b -> b.getDriver().getId().equals(driver.getId()))
                    .sorted(Comparator.comparing(Booking::getPickupTime)).toList();
            Booking current = assigned.stream().filter(this::unfinishedTrip).findFirst().orElse(null);
            Booking next = assigned.stream().filter(b -> b.getPickupTime().isAfter(clock.instant()))
                    .findFirst().orElse(null);
            Booking conflict = assigned.stream().filter(b -> !Objects.equals(b.getId(), excludeBookingId))
                    .filter(b -> unfinishedTrip(b) || AvailabilityPolicy.overlaps(vehicle, start, end,
                            b.getVehicle(), b.getPickupTime(), ends.get(b.getId())))
                    .sorted(Comparator.comparing((Booking b) -> unfinishedTrip(b)).reversed()
                            .thenComparing(Booking::getPickupTime))
                    .findFirst().orElse(null);
            DriverActivityStatus work = driver.getActivityStatus();
            String status = current != null ? "ON_TRIP"
                    : work == DriverActivityStatus.OFFLINE ? "OFFLINE"
                    : work != DriverActivityStatus.AVAILABLE ? "UNAVAILABLE"
                    : conflict == null ? "AVAILABLE"
                    : conflict.getStatus() == BookingStatus.DRIVER_ASSIGNED ? "PENDING_ASSIGNMENT" : "SCHEDULE_CONFLICT";
            String reason = conflict != null && unfinishedTrip(conflict) ? "DRIVER_ON_TRIP"
                    : work == DriverActivityStatus.OFFLINE ? "DRIVER_OFFLINE"
                    : work != DriverActivityStatus.AVAILABLE ? "DRIVER_UNAVAILABLE"
                    : conflict == null ? null
                    : conflict.getStatus() == BookingStatus.DRIVER_ASSIGNED ? "ASSIGNMENT_PENDING" : "DRIVER_SCHEDULE_CONFLICT";
            result.put(driver.getId(), new AssignmentAvailabilityResponse(work == null ? "UNAVAILABLE" : work.name(),
                    status, reason, current == null ? null : current.getBookingCode(),
                    current == null ? null : current.getActualStartAt(),
                    conflict == null ? null : conflict.getBookingCode(), conflict == null ? null : conflict.getPickupTime(),
                    conflict == null ? null : ends.get(conflict.getId()), next == null ? null : next.getBookingCode(),
                    next == null ? null : next.getPickupTime(),
                    conflict == null || unfinishedTrip(conflict) ? null
                            : AvailabilityPolicy.bufferedWindow(conflict.getVehicle(), conflict.getPickupTime(), ends.get(conflict.getId())).end(),
                    now));
        }
        return result;
    }

    private boolean unfinishedTrip(Booking booking) {
        return booking.getStatus() == BookingStatus.IN_PROGRESS
                || booking.getStatus() == BookingStatus.AWAITING_END_CONFIRMATION;
    }

    private boolean holdsSlot(Booking booking, Instant now) {
        return booking.getStatus() != BookingStatus.DRIVER_ASSIGNED
                || booking.getAssignmentExpiresAt() == null || booking.getAssignmentExpiresAt().isAfter(now);
    }

    @Override
    public List<Driver> findAvailable(Long providerId, Vehicle vehicle, Instant start, Instant end,
                                      Long excludeBookingId) {
        List<Driver> drivers = driverAssignmentService.listEligible(providerId, start.atZone(VIETNAM_ZONE).toLocalDate());
        var availability = inspect(providerId, drivers, vehicle, start, end, excludeBookingId);
        return drivers.stream().filter(driver -> availability.get(driver.getId()).reason() == null).toList();
    }

    @Override
    public boolean isAvailable(Long driverId, Vehicle vehicle, Instant start, Instant end,
                               Long excludeBookingId) {
        AvailabilityPolicy.Window targetWindow = AvailabilityPolicy.bufferedWindow(vehicle, start, end);
        return bookingRepository.findPotentialDriverConflicts(
                        driverId, OCCUPYING_STATUSES, excludeBookingId, targetWindow.end()).stream()
                .filter(existing -> holdsSlot(existing, clock.instant()))
                .noneMatch(existing -> unfinishedTrip(existing) || AvailabilityPolicy.overlaps(
                        vehicle, start, end, existing.getVehicle(), existing.getPickupTime(), plannedEnd(existing)));
    }

    private Instant plannedEnd(Booking booking) {
        BookingPolicySnapshot snapshot = snapshotRepository.findByBooking_Id(booking.getId()).orElse(null);
        int slotLength = snapshot != null
                ? snapshot.getSlotLengthMinutes()
                : booking.getPlan() == null ? 60 : booking.getPlan().getSlotLengthMinutes();
        int quantity = chargeRepository.findByBooking_IdAndChargeType(booking.getId(), ChargeType.BASE_RENTAL)
                .map(BookingCharge::getQuantity)
                .orElse(1);
        return booking.getPickupTime().plus(Duration.ofMinutes((long) slotLength * Math.max(1, quantity)));
    }
}
