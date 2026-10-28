package com.fptufa26.ecomotion.notification.external.service.implement;

import com.fptufa26.ecomotion.notification.external.dto.request.OtpNotificationRequest;
import com.fptufa26.ecomotion.notification.external.dto.request.BookingNotificationRequest;
import com.fptufa26.ecomotion.notification.external.dto.response.NotificationResult;
import com.fptufa26.ecomotion.notification.external.enums.NotificationChannel;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.stream.Stream;
import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class SmtpEmailNotificationServiceImplTest {

    private static final String FROM = "no-reply@ecomotion.test";
    private static final OtpNotificationRequest REQUEST = new OtpNotificationRequest(
            "customer@example.com", "482915", 300, NotificationChannel.EMAIL);
    private static final BookingNotificationRequest BOOKING = new BookingNotificationRequest(
            "renter@example.com", "EM-2026-000001", "VinFast VF 8", "Điểm đón",
            Instant.parse("2026-12-01T01:00:00Z"), new BigDecimal("300000"), NotificationChannel.EMAIL);
    private static ValidatorFactory validatorFactory;
    private JavaMailSender mailSender;
    private SmtpEmailNotificationServiceImpl service;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        service = new SmtpEmailNotificationServiceImpl(mailSender, validatorFactory.getValidator(), FROM);
    }

    @Test
    void sendsPlainTextMessage() {
        NotificationResult result = service.sendOtp(REQUEST);

        assertThat(result.success()).isTrue();
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo(FROM);
        assertThat(message.getTo()).containsExactly(REQUEST.recipient());
        assertThat(message.getSubject()).isEqualTo("EcoMotion verification code");
        assertThat(message.getText()).contains(REQUEST.code()).contains("300 seconds");
        verifyNoMoreInteractions(mailSender);
    }

    @Test
    void returnsFailureWithoutExposingSmtpDetailsOrRetrying() {
        doThrow(new MailSendException("Internal SMTP server details"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        NotificationResult result = service.sendOtp(REQUEST);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("Could not send email notification");
        verify(mailSender).send(any(SimpleMailMessage.class));
        verifyNoMoreInteractions(mailSender);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidRequestsBeforeSending(OtpNotificationRequest request) {
        NotificationResult result = service.sendOtp(request);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isNotBlank();
        verifyNoInteractions(mailSender);
    }

    static Stream<OtpNotificationRequest> invalidRequests() {
        return Stream.of(
                null,
                new OtpNotificationRequest(null, "482915", 300, NotificationChannel.EMAIL),
                new OtpNotificationRequest(" ", "482915", 300, NotificationChannel.EMAIL),
                new OtpNotificationRequest("invalid-email", "482915", 300, NotificationChannel.EMAIL),
                new OtpNotificationRequest("a@example.com,b@example.com", "482915", 300, NotificationChannel.EMAIL),
                new OtpNotificationRequest("a@example.com", null, 300, NotificationChannel.EMAIL),
                new OtpNotificationRequest("a@example.com", " ", 300, NotificationChannel.EMAIL),
                new OtpNotificationRequest("a@example.com", "482915", 0, NotificationChannel.EMAIL),
                new OtpNotificationRequest("a@example.com", "482915", 300, null),
                new OtpNotificationRequest("a@example.com", "482915", 300, NotificationChannel.SMS)
        );
    }

    @Test
    void sendsBookingPlacedAndDepositPaidEmailsToRenterWithVietnamTime() {
        assertThat(service.sendBookingPlaced(BOOKING).success()).isTrue();
        assertThat(service.sendDepositPaid(BOOKING).success()).isTrue();
        ArgumentCaptor<SimpleMailMessage> messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        org.mockito.Mockito.verify(mailSender, org.mockito.Mockito.times(2)).send(messages.capture());
        assertThat(messages.getAllValues()).allSatisfy(message -> {
            assertThat(message.getTo()).containsExactly("renter@example.com");
            assertThat(message.getFrom()).isEqualTo(FROM);
            assertThat(message.getText()).contains("EM-2026-000001", "VinFast VF 8", "Điểm đón", "01/12/2026 08:00", "300.000 VND");
        });
        assertThat(messages.getAllValues().getFirst().getSubject()).contains("Đã tạo đơn thuê");
        assertThat(messages.getAllValues().getLast().getSubject()).contains("Đã nhận tiền cọc");
    }

    @Test
    void rejectsInvalidBookingEmailBeforeDelivery() {
        assertThat(service.sendBookingPlaced(null).success()).isFalse();
        BookingNotificationRequest invalid = new BookingNotificationRequest("invalid", BOOKING.bookingCode(),
                BOOKING.vehicleName(), BOOKING.pickupAddress(), BOOKING.pickupTime(), BOOKING.depositAmount(), BOOKING.channel());
        assertThat(service.sendDepositPaid(invalid).success()).isFalse();
        verifyNoInteractions(mailSender);
    }

    @Test
    void reportsBookingEmailDeliveryFailure() {
        doThrow(new MailSendException("Internal SMTP server details")).when(mailSender).send(any(SimpleMailMessage.class));
        NotificationResult result = service.sendDepositPaid(BOOKING);
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).doesNotContain("Internal SMTP");
    }

    @Test
    void doesNotHideUnexpectedProgrammingErrors() {
        doThrow(new IllegalStateException("Unexpected error"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> service.sendOtp(REQUEST)).isInstanceOf(IllegalStateException.class);
    }
}
