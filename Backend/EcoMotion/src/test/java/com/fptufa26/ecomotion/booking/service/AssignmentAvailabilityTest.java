package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.response.AssignmentAvailabilityResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.service.implement.DriverAvailabilityServiceImpl;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.enums.DriverActivityStatus;
import com.fptufa26.ecomotion.user.service.DriverAssignmentService;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AssignmentAvailabilityTest {
    private final Instant now = Instant.parse("2026-10-28T00:00:00Z");
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final BookingPolicySnapshotRepository snapshots = mock(BookingPolicySnapshotRepository.class);
    private final BookingChargeRepository charges = mock(BookingChargeRepository.class);
    private final DriverAvailabilityService service = new DriverAvailabilityServiceImpl(bookings, snapshots, charges,
            mock(DriverAssignmentService.class), Clock.fixed(now, ZoneOffset.UTC));
    private final Vehicle vehicle = new Vehicle();
    private final Driver driver = new Driver();

    private AssignmentAvailabilityResponse inspect() {
        driver.setId(1L);
        return service.inspect(7L, List.of(driver), vehicle, now.plusSeconds(3600), now.plusSeconds(7200), 99L).get(1L);
    }

    private Booking occupied(BookingStatus status, Instant start) {
        driver.setId(1L);
        RentalPlan plan = new RentalPlan();
        plan.setSlotLengthMinutes(60);
        Booking b = new Booking();
        b.setId(2L);
        b.setBookingCode("EM-OTHER");
        b.setDriver(driver);
        b.setVehicle(vehicle);
        b.setPlan(plan);
        b.setStatus(status);
        b.setPickupTime(start);
        when(bookings.findDriverScheduleForProvider(eq(7L), any())).thenReturn(List.of(b));
        return b;
    }

    @Test void availableWithoutSchedule() {
        assertThat(inspect().availabilityStatus()).isEqualTo("AVAILABLE");
        assertThat(inspect().reason()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = DriverActivityStatus.class, names = {"OFFLINE", "UNAVAILABLE"})
    void readinessBlocksAssignment(DriverActivityStatus readiness) {
        driver.setActivityStatus(readiness);
        assertThat(inspect().availabilityStatus()).isEqualTo(readiness.name());
        assertThat(inspect().reason()).isEqualTo("DRIVER_" + readiness.name());
    }

    @Test void conflictingScheduleHasExactWindow() {
        occupied(BookingStatus.DRIVER_CONFIRMED, now.plusSeconds(3600));
        var state = inspect();
        assertThat(state.availabilityStatus()).isEqualTo("SCHEDULE_CONFLICT");
        assertThat(state.conflictingBookingCode()).isEqualTo("EM-OTHER");
        assertThat(state.conflictStart()).isEqualTo(now.plusSeconds(3600));
        assertThat(state.conflictEnd()).isEqualTo(now.plusSeconds(7200));
    }

    @Test void tomorrowScheduleDoesNotBlockToday() {
        occupied(BookingStatus.DRIVER_CONFIRMED, now.plusSeconds(86400));
        var state = inspect();
        assertThat(state.availabilityStatus()).isEqualTo("AVAILABLE");
        assertThat(state.nextBookingCode()).isEqualTo("EM-OTHER");
        assertThat(state.nextPickupAt()).isEqualTo(now.plusSeconds(86400));
    }

    @Test void pendingAssignmentHoldsSlotUntilExpiry() {
        Booking b = occupied(BookingStatus.DRIVER_ASSIGNED, now.plusSeconds(3600));
        b.setAssignmentExpiresAt(now.plusSeconds(60));
        assertThat(inspect().availabilityStatus()).isEqualTo("PENDING_ASSIGNMENT");
        b.setAssignmentExpiresAt(now);
        assertThat(inspect().availabilityStatus()).isEqualTo("AVAILABLE");
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"IN_PROGRESS", "AWAITING_END_CONFIRMATION"})
    void unfinishedOverdueTripNeverBecomesAvailable(BookingStatus status) {
        Booking b = occupied(status, now.minusSeconds(86400));
        b.setActualStartAt(now.minusSeconds(86300));
        b.setActualEndAt(status == BookingStatus.AWAITING_END_CONFIRMATION ? now.minusSeconds(80000) : null);
        var state = inspect();
        assertThat(state.availabilityStatus()).isEqualTo("ON_TRIP");
        assertThat(state.reason()).isEqualTo("DRIVER_ON_TRIP");
        assertThat(state.currentBookingCode()).isEqualTo("EM-OTHER");
        assertThat(state.currentTripStartedAt()).isEqualTo(b.getActualStartAt());
        assertThat(state.nextAvailableAt()).isNull();
    }

    @Test void lifecycleRefreshReleasesOnlyAfterValidEndOrRejection() {
        Booking b = occupied(BookingStatus.DRIVER_ASSIGNED, now.plusSeconds(3600));
        assertThat(inspect().availabilityStatus()).isEqualTo("PENDING_ASSIGNMENT");
        b.setStatus(BookingStatus.IN_PROGRESS);
        assertThat(inspect().availabilityStatus()).isEqualTo("ON_TRIP");
        when(bookings.findDriverScheduleForProvider(eq(7L), any())).thenReturn(List.of());
        assertThat(inspect().availabilityStatus()).isEqualTo("AVAILABLE");
    }

    @Test void adjacentBookingsWithZeroBufferAreAllowed() {
        occupied(BookingStatus.DRIVER_CONFIRMED, now);
        vehicle.setMaintenanceBufferMinutes(0);
        assertThat(inspect().reason()).isNull();
        vehicle.setMaintenanceBufferMinutes(1);
        assertThat(inspect().reason()).isEqualTo("DRIVER_SCHEDULE_CONFLICT");
    }

    @Test void bufferAppliesOnceAtEachBoundary() {
        vehicle.setMaintenanceBufferMinutes(30);
        occupied(BookingStatus.DRIVER_CONFIRMED, now.minusSeconds(1800));
        assertThat(inspect().reason()).isNull();
        occupied(BookingStatus.DRIVER_CONFIRMED, now.minusSeconds(1799));
        assertThat(inspect().reason()).isEqualTo("DRIVER_SCHEDULE_CONFLICT");
    }

    @Test void batchScheduleQueriesDoNotGrowWithDriverCount() {
        occupied(BookingStatus.DRIVER_CONFIRMED, now.plusSeconds(3600));
        Driver second = new Driver();
        second.setId(3L);
        var states = service.inspect(7L, List.of(driver, second), vehicle, now.plusSeconds(3600), now.plusSeconds(7200), 99L);
        assertThat(states.get(3L).reason()).isNull();
        verify(bookings, times(1)).findDriverScheduleForProvider(eq(7L), any());
        verify(snapshots, times(1)).findByBooking_IdIn(any());
        verify(charges, times(1)).findByBooking_IdInAndChargeType(any(), any());
    }
}
