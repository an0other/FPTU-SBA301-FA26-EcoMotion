package com.fptufa26.ecomotion.notification.external.service.implement;

import com.fptufa26.ecomotion.notification.external.dto.request.BookingNotificationRequest;
import com.fptufa26.ecomotion.notification.external.dto.request.OtpNotificationRequest;
import com.fptufa26.ecomotion.notification.external.dto.response.NotificationResult;
import com.fptufa26.ecomotion.notification.external.service.BookingExternalNotificationService;
import com.fptufa26.ecomotion.notification.external.service.OtpExternalNotificationService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.fptufa26.ecomotion.notification.external.enums.NotificationChannel.EMAIL;
import static com.fptufa26.ecomotion.notification.external.enums.NotificationChannel.SMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ExternalNotificationDispatcherServiceImplTest {
    private final BookingExternalNotificationService email = mock(BookingExternalNotificationService.class);
    private final BookingExternalNotificationService sms = mock(BookingExternalNotificationService.class);
    private final BookingNotificationRequest request = new BookingNotificationRequest(
            "renter@example.com", "0912345678", "BK-123", "VinFast VF8", "Pickup address",
            Instant.parse("2026-11-06T10:00:00Z"), BigDecimal.TEN);
    private ExternalNotificationDispatcherServiceImpl dispatcher;

    @BeforeEach
    void setUp() {
        when(email.channel()).thenReturn(EMAIL);
        when(sms.channel()).thenReturn(SMS);
        dispatcher = new ExternalNotificationDispatcherServiceImpl(List.of(email, sms), List.of(EMAIL, SMS));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fallsBackAfterFailureOrException(boolean throwsException) {
        if (throwsException) {
            when(email.sendBookingPlaced(request)).thenThrow(new IllegalStateException("Unavailable"));
            when(email.sendDepositPaid(request)).thenThrow(new IllegalStateException("Unavailable"));
        } else {
            when(email.sendBookingPlaced(request)).thenReturn(new NotificationResult(false, "Unavailable"));
            when(email.sendDepositPaid(request)).thenReturn(new NotificationResult(false, "Unavailable"));
        }
        when(sms.sendBookingPlaced(request)).thenReturn(new NotificationResult(true, null));
        when(sms.sendDepositPaid(request)).thenReturn(new NotificationResult(true, null));

        assertThat(dispatcher.sendBookingPlaced(request).success()).isTrue();
        assertThat(dispatcher.sendDepositPaid(request).success()).isTrue();

        var order = inOrder(email, sms);
        order.verify(email).sendBookingPlaced(request);
        order.verify(sms).sendBookingPlaced(request);
        order.verify(email).sendDepositPaid(request);
        order.verify(sms).sendDepositPaid(request);
    }

    @Test
    void stopsAfterFirstSuccess() {
        when(email.sendBookingPlaced(request)).thenReturn(new NotificationResult(true, null));
        when(email.sendDepositPaid(request)).thenReturn(new NotificationResult(true, null));

        assertThat(dispatcher.sendBookingPlaced(request).success()).isTrue();
        assertThat(dispatcher.sendDepositPaid(request).success()).isTrue();

        verify(sms, never()).sendBookingPlaced(any());
        verify(sms, never()).sendDepositPaid(any());
    }

    @Test
    void followsConfiguredOrderInsteadOfBeanOrder() {
        dispatcher = new ExternalNotificationDispatcherServiceImpl(List.of(email, sms), List.of(SMS, EMAIL));
        when(sms.sendBookingPlaced(request)).thenReturn(new NotificationResult(true, null));

        assertThat(dispatcher.sendBookingPlaced(request).success()).isTrue();

        verify(email, never()).sendBookingPlaced(any());
    }

    @Test
    void skipsMissingOrUnsupportedServices() {
        var otpOnly = mock(OtpExternalNotificationService.class);
        when(otpOnly.channel()).thenReturn(EMAIL);
        when(sms.sendBookingPlaced(request)).thenReturn(new NotificationResult(true, null));
        var missing = new ExternalNotificationDispatcherServiceImpl(List.of(sms), List.of(EMAIL, SMS));
        var unsupported = new ExternalNotificationDispatcherServiceImpl(List.of(otpOnly, sms), List.of(EMAIL, SMS));

        assertThat(missing.sendBookingPlaced(request).success()).isTrue();
        assertThat(unsupported.sendBookingPlaced(request).success()).isTrue();
        verify(otpOnly, never()).sendOtp(any());
    }

    @Test
    void returnsFailureWhenAllChannelsFail() {
        var failure = new NotificationResult(false, "SMS unavailable");
        when(email.sendBookingPlaced(request)).thenReturn(new NotificationResult(false, "Email unavailable"));
        when(sms.sendBookingPlaced(request)).thenReturn(failure);

        assertThat(dispatcher.sendBookingPlaced(request)).isEqualTo(failure);
    }

    @Test
    void returnsFailureWhenEveryChannelThrows() {
        when(email.sendDepositPaid(request)).thenThrow(new IllegalStateException("Unavailable"));
        when(sms.sendDepositPaid(request)).thenThrow(new IllegalStateException("Unavailable"));

        assertThat(dispatcher.sendDepositPaid(request).success()).isFalse();
    }

    @Test
    void returnsFailureWithoutAvailableServicesOrRequest() {
        dispatcher = new ExternalNotificationDispatcherServiceImpl(List.of(), List.of(EMAIL, SMS));

        assertThat(dispatcher.sendBookingPlaced(request).success()).isFalse();
        assertThat(dispatcher.sendBookingPlaced(null).success()).isFalse();
        assertThat(dispatcher.sendDepositPaid(null).success()).isFalse();
    }

    @Test
    void providersUseTheirOwnRecipientAndValidateMissingContacts() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var mailSender = mock(JavaMailSender.class);
            var smtp = new SmtpEmailNotificationServiceImpl(mailSender, validator, "no-reply@ecomotion.vn");
            var mockSms = new MockSmsNotificationServiceImpl(validator);
            var loggingEmail = new LoggingEmailNotificationServiceImpl(validator);
            var realDispatcher = new ExternalNotificationDispatcherServiceImpl(List.of(smtp, mockSms), List.of(EMAIL, SMS));

            assertThat(realDispatcher.sendBookingPlaced(request).success()).isTrue();
            verify(mailSender).send(argThat((SimpleMailMessage message) ->
                    List.of(message.getTo()).equals(List.of(request.email()))));

            var phoneOnly = new BookingNotificationRequest(null, request.phone(), request.bookingCode(),
                    request.vehicleName(), request.pickupAddress(), request.pickupTime(), request.depositAmount());
            assertThat(realDispatcher.sendDepositPaid(phoneOnly).success()).isTrue();
            assertThat(loggingEmail.sendBookingPlaced(phoneOnly).success()).isFalse();
            var noContacts = new BookingNotificationRequest("", null, request.bookingCode(),
                    request.vehicleName(), request.pickupAddress(), request.pickupTime(), request.depositAmount());
            assertThat(realDispatcher.sendBookingPlaced(noContacts).success()).isFalse();
            var invalidBooking = new BookingNotificationRequest(request.email(), request.phone(), "",
                    request.vehicleName(), request.pickupAddress(), request.pickupTime(), request.depositAmount());
            assertThat(realDispatcher.sendBookingPlaced(invalidBooking).success()).isFalse();
            verifyNoMoreInteractions(mailSender);
        }
    }

    @Test
    void otpStillUsesOnlyRequestedChannel() {
        var otpEmail = mock(OtpExternalNotificationService.class);
        var otpSms = mock(OtpExternalNotificationService.class);
        when(otpEmail.channel()).thenReturn(EMAIL);
        when(otpSms.channel()).thenReturn(SMS);
        var otp = new OtpNotificationRequest("renter@example.com", "123456", 60, EMAIL);
        var failure = new NotificationResult(false, "Unavailable");
        when(otpEmail.sendOtp(otp)).thenReturn(failure);
        dispatcher = new ExternalNotificationDispatcherServiceImpl(List.of(otpEmail, otpSms), List.of(EMAIL, SMS));

        assertThat(dispatcher.sendOtp(otp)).isEqualTo(failure);
        verify(otpSms, never()).sendOtp(any());
    }
}
