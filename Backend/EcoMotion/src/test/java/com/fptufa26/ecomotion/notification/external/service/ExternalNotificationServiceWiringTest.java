package com.fptufa26.ecomotion.notification.external.service;

import com.fptufa26.ecomotion.notification.external.dto.request.OtpNotificationRequest;
import com.fptufa26.ecomotion.notification.external.dto.request.BookingNotificationRequest;
import com.fptufa26.ecomotion.notification.external.enums.NotificationChannel;
import com.fptufa26.ecomotion.notification.external.service.implement.SmtpEmailNotificationServiceImpl;
import com.fptufa26.ecomotion.notification.external.service.implement.LoggingEmailNotificationServiceImpl;
import com.fptufa26.ecomotion.notification.external.service.implement.MockSmsNotificationServiceImpl;
import com.fptufa26.ecomotion.notification.external.service.implement.ExternalNotificationDispatcherServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.HashMap;
import java.util.Map;
import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ExternalNotificationServiceWiringTest {

    private static final OtpNotificationRequest EMAIL_REQUEST = new OtpNotificationRequest(
            "customer@example.com", "482915", 300, NotificationChannel.EMAIL);
    private static final OtpNotificationRequest SMS_REQUEST = new OtpNotificationRequest(
            "0901234567", "739215", 300, NotificationChannel.SMS);
    private static final BookingNotificationRequest BOOKING = new BookingNotificationRequest(
            "renter@example.com", "EM-2026-000001", "VinFast VF 8", "Điểm đón",
            Instant.parse("2026-12-01T01:00:00Z"), new BigDecimal("300000"), NotificationChannel.EMAIL);

    @Test
    void routesBookingNotificationsThroughSelectedEmailProvider() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        try (AnnotationConfigApplicationContext context = createContext(mailSender,
                Map.of("app.notification.external.email.mode", "smtp"))) {
            ExternalNotificationDispatcherService dispatcher = context.getBean(ExternalNotificationDispatcherService.class);
            assertThat(dispatcher.sendBookingPlaced(BOOKING).success()).isTrue();
            assertThat(dispatcher.sendDepositPaid(BOOKING).success()).isTrue();
            org.mockito.Mockito.verify(mailSender, org.mockito.Mockito.times(2)).send(
                    org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
        }
    }

    @Test
    void loggingBookingNotificationsDoNotSendRealMailAndInvalidChannelIsRejected() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        try (AnnotationConfigApplicationContext context = createContext(mailSender, Map.of())) {
            ExternalNotificationDispatcherService dispatcher = context.getBean(ExternalNotificationDispatcherService.class);
            assertThat(dispatcher.sendBookingPlaced(BOOKING).success()).isTrue();
            assertThat(dispatcher.sendDepositPaid(BOOKING).success()).isTrue();
            assertThat(dispatcher.sendBookingPlaced(null).success()).isFalse();
            BookingNotificationRequest sms = new BookingNotificationRequest("0901234567", BOOKING.bookingCode(),
                    BOOKING.vehicleName(), BOOKING.pickupAddress(), BOOKING.pickupTime(), BOOKING.depositAmount(), NotificationChannel.SMS);
            assertThat(dispatcher.sendBookingPlaced(sms).success()).isTrue();
            assertThat(dispatcher.sendDepositPaid(sms).success()).isTrue();
            BookingNotificationRequest invalidSms = new BookingNotificationRequest(BOOKING.recipient(), BOOKING.bookingCode(),
                    BOOKING.vehicleName(), BOOKING.pickupAddress(), BOOKING.pickupTime(), BOOKING.depositAmount(), NotificationChannel.SMS);
            assertThat(dispatcher.sendDepositPaid(invalidSms).success()).isFalse();
            verifyNoInteractions(mailSender);
        }
    }

    @Test
    void injectsDispatcherAndUsesConfiguredEmailService() {
        verifySmtpWiring(Map.of("app.mail.from", "configured@ecomotion.test"),
                "configured@ecomotion.test");
    }

    @Test
    void usesDefaultFromAddressWhenPropertyIsAbsent() {
        verifySmtpWiring(Map.of(), "no-reply@ecomotion.vn");
    }

    @Test
    void dispatchesTheRequestToTheServiceMatchingItsChannel() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        try (AnnotationConfigApplicationContext context = createContext(mailSender, Map.of(
                "app.notification.external.email.mode", "smtp", "app.mail.from", "otp@ecomotion.test"))) {
            ExternalNotificationDispatcherService dispatcher = context.getBean(ExternalNotificationDispatcherService.class);

            assertThat(dispatcher.sendOtp(EMAIL_REQUEST).success()).isTrue();
            assertThat(dispatcher.sendOtp(SMS_REQUEST).success()).isTrue();
            assertThat(context.getBeansOfType(ExternalNotificationService.class)).hasSize(2);

            verify(mailSender).send(argThat((SimpleMailMessage message) ->
                    "otp@ecomotion.test".equals(message.getFrom())
                            && message.getTo().length == 1
                            && "customer@example.com".equals(message.getTo()[0])
                            && "EcoMotion verification code".equals(message.getSubject())
                            && message.getText().contains("482915")
                            && message.getText().contains("300 seconds")));
        }
    }

    @Test
    void selectsLoggingEmailServiceWithoutSmtpDelivery() {
        verifyLoggingProvider(Map.of("app.notification.external.email.mode", "logging"));
    }

    @Test
    void defaultsToLoggingEmailServiceWhenProviderIsAbsent() {
        verifyLoggingProvider(Map.of());
    }

    @Test
    void rejectsDuplicateChannelsInFallbackOrder() {
        JavaMailSender mailSender = mock(JavaMailSender.class);

        assertThatThrownBy(() -> createContext(mailSender, Map.of(
                "app.notification.external.fallback-order", "EMAIL,EMAIL")))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("fallback order must contain unique channels");
    }

    private void verifyLoggingProvider(Map<String, Object> properties) {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        try (AnnotationConfigApplicationContext context = createContext(mailSender, properties)) {
            ExternalNotificationDispatcherService dispatcher = context.getBean(ExternalNotificationDispatcherService.class);

            assertThat(dispatcher).isInstanceOf(ExternalNotificationDispatcherServiceImpl.class);
            assertThat(context.getBeansOfType(ExternalNotificationService.class)).hasSize(2);
            assertThat(dispatcher.sendOtp(EMAIL_REQUEST).success()).isTrue();
            assertThat(dispatcher.sendOtp(SMS_REQUEST).success()).isTrue();
            assertThat(dispatcher.sendOtp(null).success()).isFalse();
            verifyNoInteractions(mailSender);
        }
    }

    private void verifySmtpWiring(Map<String, Object> properties, String expectedFrom) {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        Map<String, Object> merged = new HashMap<>(properties);
        merged.put("app.notification.external.email.mode", "smtp");
        try (AnnotationConfigApplicationContext context = createContext(mailSender, merged)) {
            ExternalNotificationDispatcherService dispatcher = context.getBean(ExternalNotificationDispatcherService.class);

            assertThat(dispatcher).isInstanceOf(ExternalNotificationDispatcherServiceImpl.class);
            assertThat(dispatcher.sendOtp(EMAIL_REQUEST).success()).isTrue();
            verify(mailSender).send(argThat((SimpleMailMessage message) ->
                    expectedFrom.equals(message.getFrom())));
        }
    }

    private AnnotationConfigApplicationContext createContext(
            JavaMailSender mailSender, Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.registerBean(JavaMailSender.class, () -> mailSender);
        context.registerBean(LocalValidatorFactoryBean.class);
        context.register(
                SmtpEmailNotificationServiceImpl.class,
                LoggingEmailNotificationServiceImpl.class,
                MockSmsNotificationServiceImpl.class,
                ExternalNotificationDispatcherServiceImpl.class);
        context.refresh();
        return context;
    }
}
