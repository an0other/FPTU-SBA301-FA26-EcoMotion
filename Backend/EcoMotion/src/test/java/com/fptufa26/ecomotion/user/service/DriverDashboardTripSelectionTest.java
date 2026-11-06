package com.fptufa26.ecomotion.user.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.service.BookingQueryService;
import com.fptufa26.ecomotion.notification.internal.service.NotificationStreamService;
import com.fptufa26.ecomotion.user.dto.response.DriverStatusResponse;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.repository.DriverRepository;
import com.fptufa26.ecomotion.user.service.implement.DriverStatusServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Chọn chuyến hiển thị trên dashboard Driver: chuyến đang thực hiện (kể cả chờ xác nhận bắt đầu)
 * tách khỏi chuyến kế tiếp; yêu cầu DRIVER_ASSIGNED hết hạn không được hiển thị.
 */
class DriverDashboardTripSelectionTest {

    static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    DriverRepository driverRepository = mock(DriverRepository.class);
    BookingQueryService bookings = mock(BookingQueryService.class);
    NotificationStreamService notifications = mock(NotificationStreamService.class);
    DriverStatusServiceImpl service = new DriverStatusServiceImpl(driverRepository, bookings, notifications,
            Clock.fixed(NOW, ZoneOffset.UTC));
    Driver driver;

    @BeforeEach
    void setup() {
        driver = new Driver();
        driver.setId(1L);
        driver.setStatus(DriverStatus.APPROVED);
        User user = new User();
        user.setId(10L);
        driver.setUser(user);
        when(driverRepository.findByUser_Id(10L)).thenReturn(Optional.of(driver));
        when(bookings.findUpcomingDriverBookings(eq(1L), anyCollection(), any())).thenReturn(List.of());
    }

    @Test
    void startedTripAwaitingStartConfirmationIsActiveAndNeverNextTrip() {
        Booking started = booking(1L, "STARTED", BookingStatus.AWAITING_START_CONFIRMATION, NOW.minusSeconds(600), null);
        Booking later = booking(2L, "LATER", BookingStatus.DRIVER_CONFIRMED, NOW.plusSeconds(7200), null);
        when(bookings.findFirstByDriverIdAndStatus(1L, BookingStatus.AWAITING_START_CONFIRMATION)).thenReturn(Optional.of(started));
        when(bookings.findUpcomingDriverBookings(eq(1L), anyCollection(), any())).thenReturn(List.of(started, later));

        DriverStatusResponse response = service.getDriverStatus(10L);

        assertThat(response.activeTrip().bookingCode()).isEqualTo("STARTED");
        assertThat(response.activeTrip().status()).isEqualTo("AWAITING_START_CONFIRMATION");
        assertThat(response.nextTrip().bookingCode()).isEqualTo("LATER");
    }

    @Test
    void inProgressTripIsActiveWithoutDependingOnOperationalStatus() {
        Booking inProgress = booking(1L, "RUNNING", BookingStatus.IN_PROGRESS, NOW.minusSeconds(900), null);
        when(bookings.findFirstByDriverIdAndStatus(1L, BookingStatus.IN_PROGRESS)).thenReturn(Optional.of(inProgress));

        DriverStatusResponse response = service.getDriverStatus(10L);

        assertThat(response.activeTrip().bookingCode()).isEqualTo("RUNNING");
        assertThat(response.nextTrip()).isNull();
    }

    @Test
    void expiredAssignmentRequestIsNotShownAsNextTrip() {
        Booking expired = booking(3L, "EXPIRED", BookingStatus.DRIVER_ASSIGNED, NOW.minusSeconds(3600), NOW.minusSeconds(1));
        when(bookings.findUpcomingDriverBookings(eq(1L), anyCollection(), any())).thenReturn(List.of(expired));

        DriverStatusResponse response = service.getDriverStatus(10L);

        assertThat(response.nextTrip()).isNull();
        assertThat(response.activeTrip()).isNull();
    }

    @Test
    void liveAssignmentRequestIsShownWithItsRealDeadline() {
        Instant deadline = NOW.plusSeconds(420);
        Booking pending = booking(4L, "PENDING", BookingStatus.DRIVER_ASSIGNED, NOW.plusSeconds(86400), deadline);
        when(bookings.findUpcomingDriverBookings(eq(1L), anyCollection(), any())).thenReturn(List.of(pending));

        DriverStatusResponse response = service.getDriverStatus(10L);

        assertThat(response.nextTrip().status()).isEqualTo("DRIVER_ASSIGNED");
        assertThat(response.nextTrip().assignmentExpiresAt()).isEqualTo(deadline);
    }

    @Test
    void confirmedTripBeatsEarlierPendingRequest() {
        Booking pendingEarlier = booking(5L, "PENDING", BookingStatus.DRIVER_ASSIGNED, NOW.plusSeconds(3600), NOW.plusSeconds(300));
        Booking confirmedLater = booking(6L, "CONFIRMED", BookingStatus.DRIVER_CONFIRMED, NOW.plusSeconds(7200), null);
        when(bookings.findUpcomingDriverBookings(eq(1L), anyCollection(), any())).thenReturn(List.of(pendingEarlier, confirmedLater));

        DriverStatusResponse response = service.getDriverStatus(10L);

        assertThat(response.nextTrip().bookingCode()).isEqualTo("CONFIRMED");
    }

    @Test
    void earliestConfirmedTripIsChosenAmongSeveralUpcoming() {
        Booking first = booking(7L, "FIRST", BookingStatus.DRIVER_CONFIRMED, NOW.plusSeconds(3600), null);
        Booking second = booking(8L, "SECOND", BookingStatus.CONFIRMED, NOW.plusSeconds(7200), null);
        when(bookings.findUpcomingDriverBookings(eq(1L), anyCollection(), any())).thenReturn(List.of(first, second));

        DriverStatusResponse response = service.getDriverStatus(10L);

        assertThat(response.nextTrip().bookingCode()).isEqualTo("FIRST");
    }

    private static Booking booking(Long id, String code, BookingStatus status, Instant pickup, Instant assignmentExpiresAt) {
        Booking booking = new Booking();
        booking.setId(id);
        booking.setBookingCode(code);
        booking.setStatus(status);
        booking.setPickupTime(pickup);
        booking.setAssignmentExpiresAt(assignmentExpiresAt);
        booking.setPickupAddress("Điểm đón " + code);
        return booking;
    }
}
