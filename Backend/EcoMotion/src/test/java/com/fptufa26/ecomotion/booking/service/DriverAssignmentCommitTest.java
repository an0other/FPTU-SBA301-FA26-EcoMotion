package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.config.OptimizationProperties;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.DriverAssignment;
import com.fptufa26.ecomotion.booking.enums.AssignmentMode;
import com.fptufa26.ecomotion.booking.enums.AssignmentStatus;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.*;
import com.fptufa26.ecomotion.booking.service.implement.DriverAvailabilityServiceImpl;
import com.fptufa26.ecomotion.booking.service.implement.DriverOptimizationServiceImpl;
import com.fptufa26.ecomotion.booking.service.implement.DriverSelectionServiceImpl;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.notification.internal.service.NotificationStreamService;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.service.DriverAssignmentService;
import com.fptufa26.ecomotion.user.service.DriverStatusService;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Regression cho điểm ghi phân công chung (commitAssignment): cọc, lịch sử, thời gian, thông báo, transaction.
 */
class DriverAssignmentCommitTest {

    static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");

    BookingRepository bookings = mock(BookingRepository.class);
    UserService users = mock(UserService.class);
    DriverAssignmentRepository assignments = mock(DriverAssignmentRepository.class);
    NotificationStreamService notifications = mock(NotificationStreamService.class);
    BookingNotificationService bookingNotifications = mock(BookingNotificationService.class);
    DriverStatusService driverStatus = mock(DriverStatusService.class);
    BookingPolicySnapshotRepository snapshots = mock(BookingPolicySnapshotRepository.class);
    BookingChargeRepository charges = mock(BookingChargeRepository.class);
    OptimizationProperties props = new OptimizationProperties();

    DriverOptimizationServiceImpl service = new DriverOptimizationServiceImpl(
            mock(com.fptufa26.ecomotion.location.service.MapLocationService.class), bookings, users, assignments,
            mock(TripIncidentRepository.class), mock(VehicleLocationRepository.class), snapshots, charges,
            mock(MapGeocodingService.class), notifications, Clock.fixed(NOW, ZoneOffset.UTC),
            bookingNotifications, props, new DriverSelectionServiceImpl(props),
            new DriverAvailabilityServiceImpl(bookings, snapshots, charges, mock(DriverAssignmentService.class),
                    Clock.fixed(NOW, ZoneOffset.UTC)),
            driverStatus);

    @BeforeEach
    void openTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void closeTransaction() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"PENDING", "ACCEPTED", "AWAITING_DEPOSIT", "REJECTED", "CANCELLED", "CANCELLATION_REVIEW", "COMPLETED"})
    void rejectsAssignmentBeforeDepositIsConfirmed(BookingStatus status) {
        Booking booking = booking(status, null);

        assertThatThrownBy(() -> service.commitAssignment(booking, driver(3L, 2L), AssignmentMode.MANUAL, NOW))
                .isInstanceOf(ConflictException.class);

        assertThat(booking.getStatus()).isEqualTo(status);
        verify(bookings, never()).saveAndFlush(any());
        verifyNoInteractions(assignments, bookingNotifications, driverStatus, notifications);
    }

    @Test
    void confirmedBookingGetsExactTimesHistoryStatusSyncAndSingleDriverNotice() {
        Booking booking = booking(BookingStatus.CONFIRMED, null);
        Driver driver = driver(3L, 2L);

        service.commitAssignment(booking, driver, AssignmentMode.MANUAL, NOW);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DRIVER_ASSIGNED);
        assertThat(booking.getDriver()).isSameAs(driver);
        assertThat(booking.getAssignedAt()).isEqualTo(NOW);
        assertThat(booking.getAssignmentExpiresAt()).isEqualTo(NOW.plusSeconds(60L * props.getAssignmentTimeoutMinutes()));
        assertThat(booking.getAssignmentMode()).isEqualTo(AssignmentMode.MANUAL);

        var captor = org.mockito.ArgumentCaptor.forClass(DriverAssignment.class);
        verify(assignments).save(captor.capture());
        DriverAssignment history = captor.getValue();
        assertThat(history.getStatus()).isEqualTo(AssignmentStatus.PENDING_RESPONSE);
        assertThat(history.getAssignedAt()).isEqualTo(NOW);
        assertThat(history.getExpiresAt()).isEqualTo(booking.getAssignmentExpiresAt());
        assertThat(history.getRoundNumber()).isEqualTo(1);

        verify(driverStatus).updateStatusTimestamp(3L);
        verify(bookingNotifications).driverAssigned(booking);
        verify(bookingNotifications, never()).changed(any(), any());
    }

    @Test
    void reassignmentRecallsPreviousDriverNoticeOnceAndKeepsItsHistory() {
        Driver previous = driver(9L, 99L);
        Booking booking = booking(BookingStatus.DRIVER_ASSIGNED, previous);
        DriverAssignment old = new DriverAssignment();
        old.setBooking(booking);
        old.setDriver(previous);
        old.setStatus(AssignmentStatus.PENDING_RESPONSE);
        when(assignments.findByBooking_IdOrderByCreatedAtDesc(4L)).thenReturn(List.of(old));

        service.commitAssignment(booking, driver(3L, 2L), AssignmentMode.FULL_AUTO, NOW);

        assertThat(old.getStatus()).isEqualTo(AssignmentStatus.RECALLED);
        verify(assignments, never()).delete(any());
        verify(notifications, times(1)).sendEventToUser(eq(99L), eq("DRIVER_REASSIGNED"), anyMap());
        verify(notifications, never()).sendEventToUser(eq(2L), eq("DRIVER_REASSIGNED"), anyMap());
    }

    @Test
    void reselectingTheSameDriverRecallsOldRecordWithoutReassignedNoticeToThatDriver() {
        Driver same = driver(3L, 2L);
        Booking booking = booking(BookingStatus.DRIVER_ASSIGNED, same);
        DriverAssignment old = new DriverAssignment();
        old.setBooking(booking);
        old.setDriver(same);
        old.setStatus(AssignmentStatus.ACCEPTED);
        when(assignments.findByBooking_IdOrderByCreatedAtDesc(4L)).thenReturn(List.of(old));

        service.commitAssignment(booking, same, AssignmentMode.MANUAL, NOW);

        assertThat(old.getStatus()).isEqualTo(AssignmentStatus.RECALLED);
        verify(notifications, never()).sendEventToUser(eq(2L), eq("DRIVER_REASSIGNED"), anyMap());
    }

    @Test
    void assignedAtStaysAfterPreviousAssignmentWhenClockDoesNotAdvance() {
        Booking booking = booking(BookingStatus.CONFIRMED, null);
        booking.setAssignedAt(NOW);

        service.commitAssignment(booking, driver(3L, 2L), AssignmentMode.MANUAL, NOW);

        assertThat(booking.getAssignedAt()).isEqualTo(NOW.plusMillis(1));
        assertThat(booking.getAssignmentExpiresAt()).isEqualTo(NOW.plusSeconds(60L * props.getAssignmentTimeoutMinutes()));
    }

    @Test
    void commitOutsideTransactionFailsBeforeAnyWrite() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        Booking booking = booking(BookingStatus.CONFIRMED, null);

        assertThatThrownBy(() -> service.commitAssignment(booking, driver(3L, 2L), AssignmentMode.MANUAL, NOW))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(bookings, assignments, bookingNotifications, driverStatus, notifications);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    private static Driver driver(long driverId, long userId) {
        Provider provider = new Provider();
        provider.setId(1L);
        User user = new User();
        user.setId(userId);
        user.setStatus(UserStatus.ACTIVE);
        Driver driver = new Driver();
        driver.setId(driverId);
        driver.setProvider(provider);
        driver.setUser(user);
        return driver;
    }

    private static Booking booking(BookingStatus status, Driver currentDriver) {
        Provider provider = new Provider();
        provider.setId(1L);
        Vehicle vehicle = new Vehicle();
        vehicle.setProvider(provider);
        Booking booking = new Booking();
        booking.setId(4L);
        booking.setBookingCode("commit-test");
        booking.setVehicle(vehicle);
        booking.setStatus(status);
        booking.setPickupTime(Instant.parse("2026-10-29T00:00:00Z"));
        booking.setDriver(currentDriver);
        return booking;
    }
}
