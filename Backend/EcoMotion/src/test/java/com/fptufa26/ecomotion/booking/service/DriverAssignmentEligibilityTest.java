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
    DriverOptimizationServiceImpl service = new DriverOptimizationServiceImpl(bookings, users, assignments, incidents,
            mock(VehicleLocationRepository.class), mock(BookingPolicySnapshotRepository.class), mock(BookingChargeRepository.class),
            mock(MapGeocodingService.class), mock(NotificationStreamService.class), Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneOffset.UTC));
    Driver driver; Booking booking;
    @BeforeEach void setup() {
        Provider provider = new Provider(); provider.setId(1L);
        User user = new User(); user.setId(2L); user.setStatus(UserStatus.ACTIVE);
        driver = new Driver(); driver.setId(3L); driver.setProvider(provider); driver.setUser(user);
        driver.setStatus(DriverStatus.APPROVED); driver.setLicenseClass("B2"); driver.setLicenseExpiry(LocalDate.of(2030, 1, 1));
        Vehicle vehicle = new Vehicle(); vehicle.setProvider(provider); vehicle.setSeatCount(4); vehicle.setTransmission(Transmission.AUTOMATIC);
        booking = new Booking(); booking.setId(4L); booking.setVehicle(vehicle); booking.setBookingCode("test-booking");
        booking.setStatus(BookingStatus.CONFIRMED); booking.setPickupTime(Instant.parse("2026-10-29T00:00:00Z"));
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
        Booking conflict = new Booking(); conflict.setPickupTime(booking.getPickupTime()); conflict.setBookingCode("conflict");
        when(bookings.findPotentialDriverConflicts(eq(3L), anyList(), eq(4L), any())).thenReturn(List.of(conflict));
        assertThatThrownBy(() -> service.manualAssign(4L, 3L, 1L)).isInstanceOf(ConflictException.class).hasMessageContaining("conflict");
        assertThat(service.autoAssign(4L)).isNull();
    }
    @ParameterizedTest
    @ValueSource(strings = {"B", "B2"})
    void nonOverlappingFutureScheduleAllowsAnotherBooking(String licenseClass) {
        driver.setLicenseClass(licenseClass);
        Booking future = new Booking(); future.setPickupTime(booking.getPickupTime().plus(Duration.ofDays(1)));
        when(bookings.findPotentialDriverConflicts(eq(3L), anyList(), eq(4L), any())).thenReturn(List.of(future));
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
}
