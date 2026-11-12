package com.fptufa26.ecomotion.auth.service;

import com.fptufa26.ecomotion.auth.dto.request.ResetPasswordRequest;
import com.fptufa26.ecomotion.auth.repository.RefreshTokenRepository;
import com.fptufa26.ecomotion.auth.service.implement.PasswordResetServiceImpl;
import com.fptufa26.ecomotion.notification.external.dto.response.NotificationResult;
import com.fptufa26.ecomotion.notification.external.enums.NotificationChannel;
import com.fptufa26.ecomotion.notification.external.service.ExternalNotificationDispatcherService;
import com.fptufa26.ecomotion.otp.dto.request.OtpSendRequest;
import com.fptufa26.ecomotion.otp.enums.OtpPurpose;
import com.fptufa26.ecomotion.otp.service.implement.OtpServiceImpl;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.user.service.implement.UserServiceImpl;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PasswordResetOtpTest {
    @Test void resetHashesPasswordConsumesOtpAndRevokesRefreshTokens() {
        var dispatcher = mock(ExternalNotificationDispatcherService.class);
        when(dispatcher.sendOtp(any())).thenReturn(new NotificationResult(true, null));
        var otp = new OtpServiceImpl(dispatcher, 6, Duration.ofMinutes(5), 5, Duration.ofSeconds(60));
        var repository = mock(UserRepository.class);
        User user = new User();
        user.setId(9L);
        user.setPasswordHash("old-hash");
        when(repository.findByEmailIgnoreCase("renter@example.com")).thenReturn(Optional.of(user));
        var encoder = new BCryptPasswordEncoder();
        var users = new UserServiceImpl(repository, null, null, null, encoder, null, new org.springframework.core.env.StandardEnvironment(), null);
        var tokens = mock(RefreshTokenRepository.class);
        var service = new PasswordResetServiceImpl(users, otp, tokens);
        var registration = otp.send(new OtpSendRequest("renter@example.com", "renter@example.com", OtpPurpose.REGISTRATION, NotificationChannel.EMAIL));
        assertThatThrownBy(() -> service.reset(new ResetPasswordRequest("renter@example.com", registration.code(), "new-password")));
        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        var challenge = otp.send(new OtpSendRequest("renter@example.com", "renter@example.com", OtpPurpose.PASSWORD_RESET, NotificationChannel.EMAIL));
        var request = new ResetPasswordRequest("renter@example.com", challenge.code(), "new-password");
        service.reset(request);
        assertThat(encoder.matches("new-password", user.getPasswordHash())).isTrue();
        verify(tokens).revokeAllByUserId(eq(9L), any());
        assertThatThrownBy(() -> service.reset(request));
        verify(repository, times(1)).saveAndFlush(user);
    }
}
