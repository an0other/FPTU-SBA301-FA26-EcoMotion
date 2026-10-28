package com.fptufa26.ecomotion.user.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.service.BookingQueryService;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.common.service.NotificationStreamService;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.*;
import com.fptufa26.ecomotion.user.repository.DriverRepository;
import com.fptufa26.ecomotion.user.service.implement.DriverStatusServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class DriverStatusServiceTest {
    DriverRepository repository = mock(DriverRepository.class);
    BookingQueryService bookings = mock(BookingQueryService.class);
    NotificationStreamService notifications = mock(NotificationStreamService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneOffset.UTC);
    DriverStatusServiceImpl service = new DriverStatusServiceImpl(repository, bookings, notifications, clock);
    Driver driver;

    @BeforeEach void setup() {
        driver = new Driver(); driver.setId(1L); driver.setStatus(DriverStatus.APPROVED);
        User user = new User(); user.setId(10L); driver.setUser(user);
        when(repository.findByUserIdForUpdate(10L)).thenReturn(Optional.of(driver));
        when(repository.save(driver)).thenReturn(driver);
        when(bookings.findUpcomingDriverBookings(eq(1L), anyList(), any())).thenReturn(List.of());
    }
    @ParameterizedTest @EnumSource(DriverActivityStatus.class)
    void readinessIsPersistedUnderDriverLock(DriverActivityStatus status) {
        var response = service.updateActivityStatus(10L, status);
        assertThat(response.activityStatus()).isEqualTo(status);
        assertThat(response.operationalStatus().name()).isEqualTo(status.name());
        assertThat(driver.getStatusUpdatedAt()).isEqualTo(clock.instant());
        verify(repository).findByUserIdForUpdate(10L);
        verify(repository, never()).findByUser_Id(anyLong());
    }
    @Test void activeTripCannotBeOverridden() {
        when(bookings.existsByDriverIdAndStatus(1L, BookingStatus.IN_PROGRESS)).thenReturn(true);
        assertThatThrownBy(() -> service.updateActivityStatus(10L, DriverActivityStatus.AVAILABLE)).isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }
    @Test void awaitingValidEndStillBlocksReadinessChanges() {
        when(bookings.existsByDriverIdAndStatus(1L, BookingStatus.AWAITING_END_CONFIRMATION)).thenReturn(true);
        assertThat(service.calculateOperationalStatus(driver)).isEqualTo(DriverOperationalStatus.ON_TRIP);
        assertThatThrownBy(() -> service.updateActivityStatus(10L, DriverActivityStatus.AVAILABLE)).isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }
    @Test void futureScheduleIsDerivedAndCompletionRecalculates() {
        when(bookings.findUpcomingDriverBookings(eq(1L), anyList(), any())).thenReturn(List.of(new Booking()));
        assertThat(service.calculateOperationalStatus(driver)).isEqualTo(DriverOperationalStatus.SCHEDULED);
        when(bookings.existsByDriverIdAndStatus(1L, BookingStatus.IN_PROGRESS)).thenReturn(true);
        assertThat(service.calculateOperationalStatus(driver)).isEqualTo(DriverOperationalStatus.ON_TRIP);
        when(bookings.existsByDriverIdAndStatus(1L, BookingStatus.IN_PROGRESS)).thenReturn(false);
        assertThat(service.calculateOperationalStatus(driver)).isEqualTo(DriverOperationalStatus.SCHEDULED);
        when(bookings.findUpcomingDriverBookings(eq(1L), anyList(), any())).thenReturn(List.of());
        assertThat(service.calculateOperationalStatus(driver)).isEqualTo(DriverOperationalStatus.AVAILABLE);
    }
    @Test void offlineStillDisplaysActiveTrip() {
        driver.setActivityStatus(DriverActivityStatus.OFFLINE);
        when(bookings.existsByDriverIdAndStatus(1L, BookingStatus.IN_PROGRESS)).thenReturn(true);
        assertThat(service.calculateOperationalStatus(driver)).isEqualTo(DriverOperationalStatus.ON_TRIP);
    }
}
