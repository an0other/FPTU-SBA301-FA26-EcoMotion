package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.service.implement.BookingNotificationServiceImpl;
import com.fptufa26.ecomotion.notification.external.dto.request.BookingNotificationRequest;
import com.fptufa26.ecomotion.notification.external.dto.response.NotificationResult;
import com.fptufa26.ecomotion.notification.external.enums.NotificationChannel;
import com.fptufa26.ecomotion.notification.external.service.ExternalNotificationDispatcherService;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class BookingNotificationServiceTest {
    private ExternalNotificationDispatcherService dispatcher;
    private BookingNotificationService service;
    private Booking booking;

    @BeforeEach
    void setUp() {
        dispatcher = mock(ExternalNotificationDispatcherService.class);
        service = new BookingNotificationServiceImpl(dispatcher);
        User user = new User();
        user.setEmail("renter@example.com");
        user.setPhone("0901234567");
        Renter renter = new Renter();
        renter.setUser(user);
        Vehicle vehicle = new Vehicle();
        vehicle.setBrand("VinFast");
        vehicle.setModel("VF 8");
        booking = new Booking();
        booking.setRenter(renter);
        booking.setVehicle(vehicle);
        booking.setBookingCode("EM-2026-000001");
        booking.setPickupAddress("Điểm đón");
        booking.setPickupTime(Instant.parse("2026-12-01T01:00:00Z"));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void cleanUp() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void placedEmailIsSentOnlyAfterCommitWithCopiedRenterData() {
        when(dispatcher.sendBookingPlaced(any())).thenReturn(new NotificationResult(true, null));
        service.bookingPlaced(booking, new BigDecimal("300000"));
        booking.setBookingCode("changed");
        booking.getRenter().getUser().setEmail("changed@example.com");
        verifyNoInteractions(dispatcher);

        TransactionSynchronizationUtils.triggerAfterCommit();

        ArgumentCaptor<BookingNotificationRequest> request = ArgumentCaptor.forClass(BookingNotificationRequest.class);
        verify(dispatcher).sendBookingPlaced(request.capture());
        assertThat(request.getValue().recipient()).isEqualTo("renter@example.com");
        assertThat(request.getValue().bookingCode()).isEqualTo("EM-2026-000001");
        assertThat(request.getValue().channel()).isEqualTo(NotificationChannel.EMAIL);
        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    void rolledBackBookingOrPaymentDoesNotSendEmail() {
        service.bookingPlaced(booking, new BigDecimal("300000"));
        service.depositPaid(booking, new BigDecimal("300000"));
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verifyNoInteractions(dispatcher);
    }

    @Test
    void emailFailureDoesNotFailAnAlreadyCommittedDeposit() {
        when(dispatcher.sendDepositPaid(any())).thenThrow(new IllegalStateException("SMTP unavailable"));
        doReturn(new NotificationResult(true, null)).when(dispatcher)
                .sendDepositPaid(argThat(request -> request != null && request.channel() == NotificationChannel.SMS));
        service.depositPaid(booking, new BigDecimal("300000"));
        assertThatCode(TransactionSynchronizationUtils::triggerAfterCommit).doesNotThrowAnyException();
        verify(dispatcher).sendDepositPaid(argThat(request -> request.channel() == NotificationChannel.EMAIL));
        verify(dispatcher).sendDepositPaid(argThat(request -> request.channel() == NotificationChannel.SMS
                && request.recipient().equals("0901234567")));
        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    void allChannelFailuresAreHandledWithoutRetry() {
        when(dispatcher.sendBookingPlaced(any())).thenReturn(new NotificationResult(false, "Delivery failed"));
        service.bookingPlaced(booking, new BigDecimal("300000"));
        assertThatCode(TransactionSynchronizationUtils::triggerAfterCommit).doesNotThrowAnyException();
        verify(dispatcher, times(2)).sendBookingPlaced(any());
        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    void failedPlacedEmailFallsBackToCopiedRenterPhoneAfterCommit() {
        when(dispatcher.sendBookingPlaced(any())).thenReturn(new NotificationResult(false, "Email failed"));
        when(dispatcher.sendBookingPlaced(argThat(request -> request != null && request.channel() == NotificationChannel.SMS)))
                .thenReturn(new NotificationResult(true, null));
        service.bookingPlaced(booking, new BigDecimal("300000"));
        booking.getRenter().getUser().setPhone("0912345678");
        verifyNoInteractions(dispatcher);
        TransactionSynchronizationUtils.triggerAfterCommit();
        var order = inOrder(dispatcher);
        order.verify(dispatcher).sendBookingPlaced(argThat(request -> request.channel() == NotificationChannel.EMAIL
                && request.recipient().equals("renter@example.com")));
        order.verify(dispatcher).sendBookingPlaced(argThat(request -> request.channel() == NotificationChannel.SMS
                && request.recipient().equals("0901234567")
                && request.depositAmount().compareTo(new BigDecimal("300000")) == 0));
        order.verifyNoMoreInteractions();
    }

    @Test
    void missingPhoneDoesNotSendSmsToEmailAddress() {
        booking.getRenter().getUser().setPhone(null);
        when(dispatcher.sendBookingPlaced(any())).thenReturn(new NotificationResult(false, "Email failed"));
        service.bookingPlaced(booking, new BigDecimal("300000"));
        TransactionSynchronizationUtils.triggerAfterCommit();
        verify(dispatcher).sendBookingPlaced(argThat(request -> request.channel() == NotificationChannel.EMAIL));
        verifyNoMoreInteractions(dispatcher);
    }
}
