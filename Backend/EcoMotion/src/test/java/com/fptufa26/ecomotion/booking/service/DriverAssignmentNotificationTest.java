package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.service.implement.BookingNotificationServiceImpl;
import com.fptufa26.ecomotion.notification.external.service.ExternalNotificationDispatcherService;
import com.fptufa26.ecomotion.notification.internal.service.NotificationStreamService;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Quy tắc thông báo phân công: Driver mới nhận DRIVER_ASSIGNED đúng một lần;
 * Provider và Renter nhận BOOKING_CHANGED đúng một lần; không bên nào nhận trùng.
 */
class DriverAssignmentNotificationTest {

    @Test
    void newDriverGetsDriverAssignedOnceAndProviderAndRenterGetBookingChangedOnce() {
        NotificationStreamService stream = mock(NotificationStreamService.class);
        var notifications = new BookingNotificationServiceImpl(mock(ExternalNotificationDispatcherService.class), stream);

        Booking booking = bookingFor(BookingStatus.DRIVER_ASSIGNED, 10L, 20L, 30L);
        notifications.driverAssigned(booking);

        verify(stream).sendEventToProvider(eq(10L), eq("BOOKING_CHANGED"), anyMap());
        verify(stream).sendEventToUser(eq(20L), eq("BOOKING_CHANGED"), anyMap());
        verify(stream).sendEventToUser(eq(30L), eq("DRIVER_ASSIGNED"), anyMap());
        verifyNoMoreInteractions(stream);
    }

    @Test
    void driverNeverReceivesBookingChangedForTheSameAssignment() {
        NotificationStreamService stream = mock(NotificationStreamService.class);
        var notifications = new BookingNotificationServiceImpl(mock(ExternalNotificationDispatcherService.class), stream);

        notifications.driverAssigned(bookingFor(BookingStatus.DRIVER_ASSIGNED, 10L, 20L, 30L));

        verify(stream, never()).sendEventToUser(eq(30L), eq("BOOKING_CHANGED"), any(Map.class));
    }

    private static Booking bookingFor(BookingStatus status, long providerId, long renterUserId, long driverUserId) {
        Provider provider = mock(Provider.class);
        when(provider.getId()).thenReturn(providerId);
        Vehicle vehicle = mock(Vehicle.class);
        when(vehicle.getProvider()).thenReturn(provider);

        User renterUser = mock(User.class);
        when(renterUser.getId()).thenReturn(renterUserId);
        Renter renter = mock(Renter.class);
        when(renter.getUser()).thenReturn(renterUser);

        User driverUser = mock(User.class);
        when(driverUser.getId()).thenReturn(driverUserId);
        Driver driver = mock(Driver.class);
        when(driver.getUser()).thenReturn(driverUser);

        Booking booking = mock(Booking.class);
        when(booking.getVehicle()).thenReturn(vehicle);
        when(booking.getRenter()).thenReturn(renter);
        when(booking.getDriver()).thenReturn(driver);
        when(booking.getStatus()).thenReturn(status);
        when(booking.getBookingCode()).thenReturn("EM-TEST-0001");
        return booking;
    }
}
