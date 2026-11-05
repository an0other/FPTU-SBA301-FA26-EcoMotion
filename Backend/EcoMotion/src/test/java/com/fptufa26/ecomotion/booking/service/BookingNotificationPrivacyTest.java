package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.service.implement.BookingNotificationServiceImpl;
import com.fptufa26.ecomotion.notification.external.service.ExternalNotificationDispatcherService;
import com.fptufa26.ecomotion.notification.internal.service.NotificationStreamService;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingNotificationPrivacyTest {
    @Test void bookingEventOnlyTargetsItsParticipantsAndContainsNoOtherBooking() {
        var stream = mock(NotificationStreamService.class);
        var service = new BookingNotificationServiceImpl(mock(ExternalNotificationDispatcherService.class), stream);
        var user = new User(); user.setId(10L);
        var renter = new Renter(); renter.setUser(user);
        var provider = new Provider(); provider.setId(20L);
        var vehicle = new Vehicle(); vehicle.setProvider(provider);
        var booking = new Booking(); booking.setId(30L); booking.setBookingCode("OWN-BOOKING");
        booking.setRenter(renter); booking.setVehicle(vehicle); booking.setStatus(BookingStatus.CONFIRMED);

        service.changed(booking, "BOOKING_CHANGED");

        verify(stream).sendEventToUser(eq(10L), eq("BOOKING_CHANGED"), argThat(payload -> {
            assertThat(((Map<?, ?>) payload).get("bookingCode")).isEqualTo("OWN-BOOKING");
            assertThat(payload.toString()).doesNotContain("OTHER-BOOKING", "operationalRisk", "blockingBookingCode");
            return true;
        }));
        verify(stream).sendEventToProvider(eq(20L), eq("BOOKING_CHANGED"), any());
        verifyNoMoreInteractions(stream);
    }
}
