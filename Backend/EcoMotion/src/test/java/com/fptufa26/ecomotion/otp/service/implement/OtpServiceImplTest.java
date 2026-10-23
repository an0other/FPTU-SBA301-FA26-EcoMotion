package com.fptufa26.ecomotion.otp.service.implement;

import com.fptufa26.ecomotion.notification.external.dto.request.OtpNotificationRequest;
import com.fptufa26.ecomotion.notification.external.enums.NotificationChannel;
import com.fptufa26.ecomotion.notification.external.exception.NotificationDeliveryException;
import com.fptufa26.ecomotion.otp.dto.request.OtpSendRequest;
import com.fptufa26.ecomotion.otp.dto.request.OtpVerifyRequest;
import com.fptufa26.ecomotion.otp.dto.response.OtpChallenge;
import com.fptufa26.ecomotion.otp.enums.OtpPurpose;
import com.fptufa26.ecomotion.otp.exception.OtpExpiredException;
import com.fptufa26.ecomotion.otp.exception.OtpResendTooSoonException;
import com.fptufa26.ecomotion.otp.service.OtpService;
import com.fptufa26.ecomotion.notification.external.dto.response.NotificationResult;
import com.fptufa26.ecomotion.notification.external.service.ExternalNotificationDispatcherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceImplTest {

    @Mock
    private ExternalNotificationDispatcherService externalNotificationDispatcherService;

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpServiceImpl(externalNotificationDispatcherService,
                6, Duration.ofMinutes(5), 5, Duration.ofSeconds(60));
    }

    @Test
    void sendsGeneratedCodeDirectlyThroughNotificationService() {
        when(externalNotificationDispatcherService.sendOtp(any(OtpNotificationRequest.class)))
                .thenReturn(new NotificationResult(true, null));

        OtpChallenge challenge = otpService.send(emailRequest(OtpPurpose.REGISTRATION));

        assertThat(challenge.code()).matches("\\d{6}");
        verify(externalNotificationDispatcherService).sendOtp(argThat(request ->
                request.recipient().equals("customer@example.com")
                        && request.channel() == NotificationChannel.EMAIL
                        && request.code().equals(challenge.code())
                        && request.expiresInSeconds() == 300));
        assertThat(otpService.verify(new OtpVerifyRequest(
                "customer@example.com", challenge.code(), OtpPurpose.REGISTRATION)).recipient())
                .isEqualTo("customer@example.com");
    }

    @Test
    void failedDeliveryDoesNotStoreCodeOrStartCooldown() {
        when(externalNotificationDispatcherService.sendOtp(any(OtpNotificationRequest.class)))
                .thenReturn(new NotificationResult(false, "Could not send email notification"))
                .thenReturn(new NotificationResult(true, null));

        assertThatThrownBy(() -> otpService.send(emailRequest(OtpPurpose.REGISTRATION)))
                .isInstanceOf(NotificationDeliveryException.class)
                .hasMessageContaining("customer@example.com");
        assertThatThrownBy(() -> otpService.verify(new OtpVerifyRequest(
                "customer@example.com", "111111", OtpPurpose.REGISTRATION)))
                .isInstanceOf(OtpExpiredException.class);

        OtpChallenge challenge = otpService.send(emailRequest(OtpPurpose.REGISTRATION));
        otpService.verify(new OtpVerifyRequest(
                "customer@example.com", challenge.code(), OtpPurpose.REGISTRATION));
    }

    @Test
    void successfulDeliveryStartsCooldownBeforeAnotherSend() {
        when(externalNotificationDispatcherService.sendOtp(any(OtpNotificationRequest.class)))
                .thenReturn(new NotificationResult(true, null));
        OtpChallenge challenge = otpService.send(emailRequest(OtpPurpose.REGISTRATION));

        assertThatThrownBy(() -> otpService.send(emailRequest(OtpPurpose.REGISTRATION)))
                .isInstanceOf(OtpResendTooSoonException.class);
        verify(externalNotificationDispatcherService).sendOtp(argThat(request ->
                request.recipient().equals("customer@example.com")
                        && request.channel() == NotificationChannel.EMAIL
                        && request.code().equals(challenge.code())));
        verifyNoMoreInteractions(externalNotificationDispatcherService);
    }

    @Test
    void separatesCodesByPurposeForTheSameKey() {
        when(externalNotificationDispatcherService.sendOtp(any(OtpNotificationRequest.class)))
                .thenReturn(new NotificationResult(true, null));
        OtpChallenge registration = otpService.send(emailRequest(OtpPurpose.REGISTRATION));
        OtpChallenge resume = otpService.send(emailRequest(OtpPurpose.ACTIVATION_RESUME));

        assertThat(otpService.verify(new OtpVerifyRequest(
                "customer@example.com", registration.code(), OtpPurpose.REGISTRATION)).recipient())
                .isEqualTo("customer@example.com");
        assertThat(otpService.verify(new OtpVerifyRequest(
                "customer@example.com", resume.code(), OtpPurpose.ACTIVATION_RESUME)).recipient())
                .isEqualTo("customer@example.com");
    }

    @Test
    void sendsAndVerifiesPhoneThroughTheSameApi() {
        when(externalNotificationDispatcherService.sendOtp(any(OtpNotificationRequest.class)))
                .thenReturn(new NotificationResult(true, null));
        OtpChallenge challenge = otpService.send(new OtpSendRequest(
                "42", "0901234567", OtpPurpose.PHONE_VERIFICATION, NotificationChannel.SMS));

        verify(externalNotificationDispatcherService).sendOtp(argThat(request ->
                request.recipient().equals("0901234567")
                        && request.channel() == NotificationChannel.SMS
                        && request.code().equals(challenge.code())
                        && request.expiresInSeconds() == 300));
        assertThat(challenge.expiresInSeconds()).isEqualTo(300);
        assertThat(challenge.resendCooldownSeconds()).isEqualTo(60);
        assertThat(otpService.verify(new OtpVerifyRequest(
                "42", challenge.code(), OtpPurpose.PHONE_VERIFICATION)).recipient())
                .isEqualTo("0901234567");
    }

    private OtpSendRequest emailRequest(OtpPurpose purpose) {
        return new OtpSendRequest(
                " Customer@Example.com ",
                " Customer@Example.com ",
                purpose,
                NotificationChannel.EMAIL);
    }
}
