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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InjectMocks;
import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class TripNotificationRecipientsTest {
    @Mock ExternalNotificationDispatcherService dispatcher;
    @Mock NotificationStreamService stream;
    @InjectMocks BookingNotificationServiceImpl notifications;

    private static final long PROVIDER_ID = 10L, RENTER_USER_ID = 20L, DRIVER_USER_ID = 30L;

    private Booking booking(BookingStatus status) {
        Provider provider = new Provider(); provider.setId(PROVIDER_ID);
        Vehicle vehicle = new Vehicle(); vehicle.setProvider(provider);
        User renterUser = new User(); renterUser.setId(RENTER_USER_ID);
        Renter renter = new Renter(); renter.setUser(renterUser);
        User driverUser = new User(); driverUser.setId(DRIVER_USER_ID);
        Driver driver = new Driver(); driver.setUser(driverUser);
        Booking booking = new Booking();
        booking.setBookingCode("EM-TEST-000001");
        booking.setVehicle(vehicle);
        booking.setRenter(renter);
        booking.setDriver(driver);
        booking.setStatus(status);
        return booking;
    }

    @Test void tripStartedSendsOnlyTripStartedToRenterAndSyncsProviderAndDriver() {
        Booking booking = booking(BookingStatus.IN_PROGRESS);
        booking.setActualStartAt(Instant.parse("2026-11-06T10:00:00Z"));

        notifications.tripStarted(booking);

        verify(stream).sendEventToUser(eq(RENTER_USER_ID), eq("TRIP_STARTED"), anyMap());
        verify(stream).sendEventToProvider(eq(PROVIDER_ID), eq("BOOKING_CHANGED"), anyMap());
        verify(stream).sendEventToUser(eq(DRIVER_USER_ID), eq("BOOKING_CHANGED"), anyMap());
        verify(stream, never()).sendEventToUser(eq(RENTER_USER_ID), eq("BOOKING_CHANGED"), any());
        verifyNoMoreInteractions(stream);
    }

    @Test void tripEndedSendsTripCompletedWithEndTimeToRenter() {
        Booking booking = booking(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION);
        Instant end = Instant.parse("2026-11-06T12:00:00Z");
        booking.setActualStartAt(Instant.parse("2026-11-06T10:00:00Z"));
        booking.setActualEndAt(end);

        notifications.tripEnded(booking);

        verify(stream).sendEventToUser(eq(RENTER_USER_ID), eq("TRIP_COMPLETED"),
                argThatContainsEntry("actualEndAt", end.toString()));
        verify(stream).sendEventToProvider(eq(PROVIDER_ID), eq("BOOKING_CHANGED"), anyMap());
        verify(stream).sendEventToUser(eq(DRIVER_USER_ID), eq("BOOKING_CHANGED"), anyMap());
        verify(stream, never()).sendEventToUser(eq(RENTER_USER_ID), eq("BOOKING_CHANGED"), any());
        verifyNoMoreInteractions(stream);
    }

    private static Map<String, Object> argThatContainsEntry(String key, Object value) {
        return org.mockito.ArgumentMatchers.argThat(map -> map != null && value.equals(map.get(key)));
    }
}
