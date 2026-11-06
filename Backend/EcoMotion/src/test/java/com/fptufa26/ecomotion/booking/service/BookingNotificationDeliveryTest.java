package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.service.implement.BookingNotificationServiceImpl;
import com.fptufa26.ecomotion.notification.external.dto.request.BookingNotificationRequest;
import com.fptufa26.ecomotion.notification.external.dto.response.NotificationResult;
import com.fptufa26.ecomotion.notification.external.service.ExternalNotificationDispatcherService;
import com.fptufa26.ecomotion.notification.internal.service.NotificationStreamService;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class BookingNotificationDeliveryTest {
    private final ExternalNotificationDispatcherService dispatcher = mock(ExternalNotificationDispatcherService.class);
    private final BookingNotificationServiceImpl service = new BookingNotificationServiceImpl(
            dispatcher, mock(NotificationStreamService.class));
    private Booking booking;
    private BookingNotificationRequest request;

    @BeforeEach
    void setUp() {
        var user = new User();
        user.setEmail("renter@example.com");
        user.setPhone("0912345678");
        var renter = new Renter();
        renter.setUser(user);
        var vehicle = new Vehicle();
        vehicle.setBrand("VinFast");
        vehicle.setModel("VF8");
        booking = new Booking();
        booking.setBookingCode("BK-123");
        booking.setRenter(renter);
        booking.setVehicle(vehicle);
        booking.setPickupAddress("Pickup address");
        booking.setPickupTime(Instant.parse("2026-11-06T10:00:00Z"));
        request = new BookingNotificationRequest(user.getEmail(), user.getPhone(), booking.getBookingCode(),
                "VinFast VF8", booking.getPickupAddress(), booking.getPickupTime(), BigDecimal.TEN);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void dispatchesOnceWithBothContactsWithoutTransaction() {
        when(dispatcher.sendBookingPlaced(request)).thenReturn(new NotificationResult(false, "All channels failed"));

        service.bookingPlaced(booking, BigDecimal.TEN);

        verify(dispatcher).sendBookingPlaced(request);
        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    void snapshotsContactsAndDispatchesOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        when(dispatcher.sendDepositPaid(request)).thenReturn(new NotificationResult(true, null));

        service.depositPaid(booking, BigDecimal.TEN);
        verifyNoInteractions(dispatcher);
        booking.getRenter().getUser().setEmail("changed@example.com");
        booking.getRenter().getUser().setPhone(null);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(dispatcher).sendDepositPaid(request);
        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    void deliveryExceptionDoesNotEscapeBookingService() {
        when(dispatcher.sendBookingPlaced(request)).thenThrow(new IllegalStateException("Unavailable"));

        assertThatCode(() -> service.bookingPlaced(booking, BigDecimal.TEN)).doesNotThrowAnyException();
        verify(dispatcher).sendBookingPlaced(request);
        verifyNoMoreInteractions(dispatcher);
    }
}
