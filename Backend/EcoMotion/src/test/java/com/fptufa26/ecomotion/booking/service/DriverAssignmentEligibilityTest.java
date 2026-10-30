package com.fptufa26.ecomotion.booking.service;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.*;
import com.fptufa26.ecomotion.booking.service.implement.DriverOptimizationServiceImpl;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.common.service.NotificationStreamService;
import com.fptufa26.ecomotion.user.entity.*;
import com.fptufa26.ecomotion.user.enums.*;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.Transmission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class DriverAssignmentEligibilityTest {
    BookingRepository bookings = mock(BookingRepository.class);
    UserService users = mock(UserService.class);
    DriverAssignmentRepository assignments = mock(DriverAssignmentRepository.class);
    TripIncidentRepository incidents = mock(TripIncidentRepository.class);
    NotificationStreamService notifications = mock(NotificationStreamService.class);
    BookingPolicySnapshotRepository snapshots = mock(BookingPolicySnapshotRepository.class);
    BookingChargeRepository charges = mock(BookingChargeRepository.class);
    DriverOptimizationServiceImpl service = new DriverOptimizationServiceImpl(bookings, users, assignments, incidents,
            mock(VehicleLocationRepository.class), snapshots, charges,
            mock(MapGeocodingService.class), notifications, Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneOffset.UTC),
            mock(com.fptufa26.ecomotion.booking.service.BookingNotificationService.class),
            new com.fptufa26.ecomotion.booking.config.OptimizationProperties(),
            new com.fptufa26.ecomotion.booking.service.implement.DriverSelectionServiceImpl(new com.fptufa26.ecomotion.booking.config.OptimizationProperties()),
            new com.fptufa26.ecomotion.booking.service.implement.DriverAvailabilityServiceImpl(bookings,
                    snapshots, charges,
                    mock(com.fptufa26.ecomotion.user.service.DriverAssignmentService.class),
                    Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneOffset.UTC)));
    Driver driver; Booking booking;
    @BeforeEach void setup() {
        Provider provider = new Provider(); provider.setId(1L);
        User user = new User(); user.setId(2L); user.setStatus(UserStatus.ACTIVE);
        driver = new Driver(); driver.setId(3L); driver.setProvider(provider); driver.setUser(user);
        driver.setStatus(DriverStatus.APPROVED); driver.setLicenseClass("B2"); driver.setLicenseExpiry(LocalDate.of(2030, 1, 1));
        Vehicle vehicle = new Vehicle(); vehicle.setProvider(provider); vehicle.setSeatCount(4); vehicle.setTransmission(Transmission.AUTOMATIC);
        booking = new Booking(); booking.setId(4L); booking.setVehicle(vehicle); booking.setBookingCode("test-booking");
        booking.setStatus(BookingStatus.CONFIRMED); booking.setPickupTime(Instant.parse("2026-10-29T00:00:00Z"));
        var policy = new com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot();
        policy.setSlotLengthMinutes(60);
        var base = new com.fptufa26.ecomotion.booking.entity.BookingCharge(); base.setQuantity(1);
        when(snapshots.findByBooking_Id(4L)).thenReturn(Optional.of(policy));
        when(charges.findByBooking_IdAndChargeType(eq(4L), any())).thenReturn(Optional.of(base));
        when(bookings.findByIdForUpdate(4L)).thenReturn(Optional.of(booking));
        when(bookings.findById(4L)).thenReturn(Optional.of(booking));
        when(users.findDriverByIdForUpdate(3L)).thenReturn(Optional.of(driver));
        when(users.findDriversByProviderId(1L)).thenReturn(List.of(driver));
    }
    @ParameterizedTest @EnumSource(value=DriverActivityStatus.class, names={"OFFLINE", "UNAVAILABLE"})
    void manualAndAutoRejectUnavailableDrivers(DriverActivityStatus status) {
        driver.setActivityStatus(status);
        assertThatThrownBy(() -> service.manualAssign(4L, 3L, 1L)).isInstanceOf(ConflictException.class);
        assertThat(service.autoAssign(4L)).isNull();
        verify(bookings, never()).saveAndFlush(any());
    }
    @Test void overlappingScheduleIsRejectedByBothPaths() {
        Booking conflict = new Booking(); conflict.setPickupTime(booking.getPickupTime()); conflict.setBookingCode("conflict"); conflict.setId(5L); conflict.setDriver(driver); conflict.setVehicle(booking.getVehicle()); conflict.setPlan(new com.fptufa26.ecomotion.vehicle.entity.RentalPlan()); conflict.getPlan().setSlotLengthMinutes(60);
        when(bookings.findDriverScheduleForProvider(eq(1L), anyList())).thenReturn(List.of(conflict));
        assertThatThrownBy(() -> service.manualAssign(4L, 3L, 1L)).isInstanceOf(ConflictException.class).hasMessageContaining("conflict");
        assertThat(service.autoAssign(4L)).isNull();
    }
    @ParameterizedTest
    @ValueSource(strings = {"B", "B2"})
    void nonOverlappingFutureScheduleAllowsAnotherBooking(String licenseClass) {
        driver.setLicenseClass(licenseClass);
        Booking future = new Booking(); future.setPickupTime(booking.getPickupTime().plus(Duration.ofDays(1)));
        future.setId(5L); future.setDriver(driver); future.setVehicle(booking.getVehicle());
        future.setPlan(new com.fptufa26.ecomotion.vehicle.entity.RentalPlan()); future.getPlan().setSlotLengthMinutes(60);
        future.setStatus(BookingStatus.DRIVER_CONFIRMED);
        var policy = new com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot(); policy.setBooking(future); policy.setSlotLengthMinutes(60);
        var base = new com.fptufa26.ecomotion.booking.entity.BookingCharge(); base.setBooking(future); base.setQuantity(1);
        when(snapshots.findByBooking_IdIn(any())).thenReturn(List.of(policy));
        when(charges.findByBooking_IdInAndChargeType(any(), any())).thenReturn(List.of(base));
        when(bookings.findDriverScheduleForProvider(eq(1L), anyList())).thenReturn(List.of(future));
        assertThat(service.manualAssign(4L, 3L, 1L)).isSameAs(driver);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DRIVER_ASSIGNED);
        verify(users).findDriverByIdForUpdate(3L);
    }
    @Test void autoRevalidatesAfterAcquiringDriverLock() {
        Driver offline = new Driver(); offline.setId(3L); offline.setStatus(DriverStatus.APPROVED); offline.setUser(driver.getUser()); offline.setActivityStatus(DriverActivityStatus.OFFLINE);
        when(users.findDriverByIdForUpdate(3L)).thenReturn(Optional.of(offline));
        assertThat(service.autoAssign(4L)).isNull();
        verify(users).findDriverByIdForUpdate(3L);
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test void missingRequestedDurationCannotDefaultToOneHour() {
        when(snapshots.findByBooking_Id(4L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.manualAssign(4L, 3L, 1L)).isInstanceOf(ConflictException.class);
        verify(bookings, never()).saveAndFlush(any());
    }
    @ParameterizedTest
    @EnumSource(value = DriverStatus.class, names = {"PENDING", "REJECTED", "SUSPENDED"})
    void unapprovedDriversAreExcludedFromManualAndAuto(DriverStatus status) {
        driver.setStatus(status);
        assertThatThrownBy(() -> service.manualAssign(4L, 3L, 1L)).isInstanceOf(ConflictException.class);
        assertThat(service.suggestDriver(4L, 1L).solverStatus()).isEqualTo("INFEASIBLE");
        assertThat(service.autoAssign(4L)).isNull();
    }
    @Test void providerCannotInspectAnotherProvidersBooking() {
        assertThatThrownBy(() -> service.evaluateCandidates(4L, 999L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> service.manualAssign(4L, 3L, 999L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void providerCannotAssignAnotherProvidersDriver() {
        Provider foreignProvider = new Provider(); foreignProvider.setId(999L);
        driver.setProvider(foreignProvider);
        assertThatThrownBy(() -> service.manualAssign(4L, 3L, 1L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(bookings, never()).saveAndFlush(any());
    }
    @Test void timeoutReleasesSlotAndNotifiesProviderEvenWithoutReplacement() {
        var pending = new com.fptufa26.ecomotion.booking.entity.DriverAssignment();
        pending.setDriver(driver); pending.setBooking(booking);
        pending.setStatus(com.fptufa26.ecomotion.booking.enums.AssignmentStatus.PENDING_RESPONSE);
        booking.setDriver(driver); booking.setReassignCount(3); booking.setStatus(BookingStatus.DRIVER_ASSIGNED);
        pending.setId(1L);
        Instant assigned = Instant.parse("2026-10-27T00:00:00Z");
        Instant expired = Instant.parse("2026-10-27T00:15:00Z");
        pending.setAssignedAt(assigned); pending.setExpiresAt(expired);
        booking.setAssignedAt(assigned); booking.setAssignmentExpiresAt(expired);
        when(assignments.findExpiredBookingIds(any())).thenReturn(List.of(4L));
        when(assignments.findByBooking_IdOrderByCreatedAtDesc(4L)).thenReturn(List.of(pending));
        service.processPendingAssignmentTimeouts();
        assertThat(pending.getStatus()).isEqualTo(com.fptufa26.ecomotion.booking.enums.AssignmentStatus.TIMEOUT);
        assertThat(booking.getDriver()).isNull();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookings, atLeastOnce()).saveAndFlush(booking);
    }
    @Test void licenseMustRemainValidAcrossMidnightUntilPlannedEnd() {
        booking.setPickupTime(Instant.parse("2026-10-28T16:30:00Z"));
        driver.setLicenseExpiry(LocalDate.of(2026, 10, 28));
        assertThat(service.evaluateCandidates(4L, 1L)).allMatch(c -> !c.eligible());
        assertThatThrownBy(() -> service.manualAssign(4L, 3L, 1L)).isInstanceOf(ConflictException.class);
        assertThat(service.autoAssign(4L)).isNull();
    }

    @Test
    void driverBusyNowOnTripIsEligibleForFutureBookingTomorrow() {
        Booking ongoingTrip = new Booking();
        ongoingTrip.setId(99L);
        ongoingTrip.setBookingCode("ONGOING-1");
        ongoingTrip.setStatus(BookingStatus.IN_PROGRESS);
        ongoingTrip.setPickupTime(Instant.parse("2026-10-28T00:00:00Z"));
        ongoingTrip.setActualStartAt(Instant.parse("2026-10-28T00:00:00Z"));
        ongoingTrip.setDriver(driver);
        ongoingTrip.setVehicle(booking.getVehicle());
        var ongoingPolicy = new com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot();
        ongoingPolicy.setBooking(ongoingTrip);
        ongoingPolicy.setSlotLengthMinutes(60);
        var ongoingCharge = new com.fptufa26.ecomotion.booking.entity.BookingCharge();
        ongoingCharge.setBooking(ongoingTrip);
        ongoingCharge.setQuantity(1);

        when(bookings.findDriverScheduleForProvider(eq(1L), anyList())).thenReturn(List.of(ongoingTrip));
        when(snapshots.findByBooking_IdIn(any())).thenReturn(List.of(ongoingPolicy));
        when(charges.findByBooking_IdInAndChargeType(any(), any())).thenReturn(List.of(ongoingCharge));

        var candidates = service.evaluateCandidates(4L, 1L);
        assertThat(candidates).hasSize(1);
        var c = candidates.get(0);
        assertThat(c.eligible()).isTrue();
        assertThat(c.availability().currentBookingCode()).isEqualTo("ONGOING-1");
        assertThat(c.availability().currentOperationalStatus()).isEqualTo("ON_TRIP");

        Driver assigned = service.manualAssign(4L, 3L, 1L);
        assertThat(assigned).isSameAs(driver);
        assertThat(booking.getDriver()).isSameAs(driver);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DRIVER_ASSIGNED);
    }

    @Test
    void driverAvailableNowButConflictingTomorrowIsRejected() {
        Booking futureConflict = new Booking();
        futureConflict.setId(101L);
        futureConflict.setBookingCode("CONFLICT-TOMORROW");
        futureConflict.setStatus(BookingStatus.DRIVER_CONFIRMED);
        futureConflict.setPickupTime(booking.getPickupTime());
        futureConflict.setDriver(driver);
        futureConflict.setVehicle(booking.getVehicle());
        var conflictPolicy = new com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot();
        conflictPolicy.setBooking(futureConflict);
        conflictPolicy.setSlotLengthMinutes(60);
        var conflictCharge = new com.fptufa26.ecomotion.booking.entity.BookingCharge();
        conflictCharge.setBooking(futureConflict);
        conflictCharge.setQuantity(1);

        when(bookings.findDriverScheduleForProvider(eq(1L), anyList())).thenReturn(List.of(futureConflict));
        when(snapshots.findByBooking_IdIn(any())).thenReturn(List.of(conflictPolicy));
        when(charges.findByBooking_IdInAndChargeType(any(), any())).thenReturn(List.of(conflictCharge));

        var candidates = service.evaluateCandidates(4L, 1L);
        assertThat(candidates).hasSize(1);
        var c = candidates.get(0);
        assertThat(c.eligible()).isFalse();
        assertThat(c.availability().availabilityStatus()).isEqualTo("SCHEDULE_CONFLICT");
        assertThat(c.availability().conflictingBookingCode()).isEqualTo("CONFLICT-TOMORROW");

        assertThatThrownBy(() -> service.manualAssign(4L, 3L, 1L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void driverFinishesTripBeforeBookingButViolatesBufferIsRejected() {
        booking.getVehicle().setMaintenanceBufferMinutes(30);
        Booking earlierTrip = new Booking();
        earlierTrip.setId(102L);
        earlierTrip.setBookingCode("EARLIER-TRIP");
        earlierTrip.setStatus(BookingStatus.DRIVER_CONFIRMED);
        earlierTrip.setPickupTime(booking.getPickupTime().minus(Duration.ofMinutes(45)));
        earlierTrip.setDriver(driver);
        earlierTrip.setVehicle(booking.getVehicle());
        var policy = new com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot();
        policy.setBooking(earlierTrip);
        policy.setSlotLengthMinutes(30);
        var charge = new com.fptufa26.ecomotion.booking.entity.BookingCharge();
        charge.setBooking(earlierTrip);
        charge.setQuantity(1);

        when(bookings.findDriverScheduleForProvider(eq(1L), anyList())).thenReturn(List.of(earlierTrip));
        when(snapshots.findByBooking_IdIn(any())).thenReturn(List.of(policy));
        when(charges.findByBooking_IdInAndChargeType(any(), any())).thenReturn(List.of(charge));

        var candidates = service.evaluateCandidates(4L, 1L);
        assertThat(candidates).hasSize(1);
        var c = candidates.get(0);
        assertThat(c.eligible()).isFalse();
        assertThat(c.availability().reason()).isEqualTo("DRIVER_SCHEDULE_CONFLICT");

        assertThatThrownBy(() -> service.manualAssign(4L, 3L, 1L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void noAvailableDriversReturnsInfeasibleWithoutCrash() {
        when(users.findDriversByProviderId(1L)).thenReturn(List.of());
        var suggestion = service.suggestDriver(4L, 1L);
        assertThat(suggestion.solverStatus()).isEqualTo("INFEASIBLE");
        assertThat(suggestion.suggestedDriver()).isNull();
        assertThat(suggestion.candidates()).isEmpty();

        Driver auto = service.autoAssign(4L);
        assertThat(auto).isNull();
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void cancelledBookingRejectsDriverAssignment() {
        booking.setStatus(BookingStatus.CANCELLED);
        assertThatThrownBy(() -> service.manualAssign(4L, 3L, 1L))
                .isInstanceOf(ConflictException.class);
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void driverRejectionTriggersAutoReassignAndRecallsAssignment() {
        booking.setStatus(BookingStatus.DRIVER_ASSIGNED);
        booking.setDriver(driver);
        booking.setReassignCount(0);
        when(assignments.findRejectedDriverIdsForBooking(4L)).thenReturn(List.of(3L));

        service.triggerAutoReassign(4L, 3L, "Driver rejected due to health");
        assertThat(booking.getDriver()).isNull();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getReassignCount()).isEqualTo(1);
        verify(bookings, atLeastOnce()).saveAndFlush(booking);
    }
}
