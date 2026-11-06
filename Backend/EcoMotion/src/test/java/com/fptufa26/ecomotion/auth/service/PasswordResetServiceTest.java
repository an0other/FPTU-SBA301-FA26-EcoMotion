package com.fptufa26.ecomotion.auth.service;

import com.fptufa26.ecomotion.auth.dto.request.ResetPasswordRequest;
import com.fptufa26.ecomotion.auth.repository.RefreshTokenRepository;
import com.fptufa26.ecomotion.auth.service.implement.PasswordResetServiceImpl;
import com.fptufa26.ecomotion.otp.dto.request.OtpSendRequest;
import com.fptufa26.ecomotion.otp.dto.request.OtpVerifyRequest;
import com.fptufa26.ecomotion.otp.enums.OtpPurpose;
import com.fptufa26.ecomotion.otp.exception.OtpInvalidException;
import com.fptufa26.ecomotion.otp.exception.OtpResendTooSoonException;
import com.fptufa26.ecomotion.otp.service.OtpService;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PasswordResetServiceTest {
    private final UserService users = mock(UserService.class);
    private final OtpService otp = mock(OtpService.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final PasswordResetService service = new PasswordResetServiceImpl(users, otp, refreshTokens);

    @Test void sendsOnlyForRegisteredEmailUsingSeparatePurpose() {
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);
        when(users.findByEmail("renter@example.com")).thenReturn(Optional.of(user));
        service.sendOtp(" RENTER@example.com ");
        verify(otp).send(argThat((OtpSendRequest request) -> request.key().equals("renter@example.com")
                && request.recipient().equals("renter@example.com") && request.purpose() == OtpPurpose.PASSWORD_RESET));
        service.sendOtp("unknown@example.com");
        verifyNoMoreInteractions(otp);
    }

    @Test void cooldownDoesNotRevealRegisteredAccounts() {
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);
        when(users.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(otp.send(any())).thenThrow(new OtpResendTooSoonException());
        assertThatCode(() -> service.sendOtp("renter@example.com")).doesNotThrowAnyException();
    }

    @Test void changesPasswordAndRevokesSessionsAfterVerification() {
        when(users.resetPassword("renter@example.com", "new-password")).thenReturn(7L);
        service.reset(new ResetPasswordRequest(" RENTER@example.com ", "123456", "new-password"));
        verify(otp).verify(new OtpVerifyRequest("renter@example.com", "123456", OtpPurpose.PASSWORD_RESET));

        var order = inOrder(users, refreshTokens);
        order.verify(users).resetPassword("renter@example.com", "new-password");
        order.verify(refreshTokens).revokeAllByUserId(eq(7L), any());
    }

    @Test void pendingAndLockedAccountsCannotReceivePasswordResetOtp() {
        for (UserStatus status : new UserStatus[]{UserStatus.PENDING, UserStatus.LOCKED}) {
            User user = new User();
            user.setStatus(status);
            when(users.findByEmail("renter@example.com")).thenReturn(Optional.of(user));
            service.sendOtp("renter@example.com");
        }
        verifyNoInteractions(otp);
    }

    @Test void invalidOtpCannotChangePasswordOrSessions() {
        when(otp.verify(any())).thenThrow(new OtpInvalidException());
        assertThatThrownBy(() -> service.reset(new ResetPasswordRequest("renter@example.com", "000000", "new-password")))
                .isInstanceOf(OtpInvalidException.class);
        verifyNoInteractions(users, refreshTokens);
    }

    @Test void shortPasswordDoesNotConsumeOtp() {
        assertThatThrownBy(() -> service.reset(new ResetPasswordRequest("renter@example.com", "123456", "short")))
                .hasMessageContaining("8");
        verifyNoInteractions(otp, users, refreshTokens);
    }

    @Test void oversizedUtf8PasswordDoesNotConsumeOtp() {
        assertThatThrownBy(() -> service.reset(new ResetPasswordRequest("renter@example.com", "123456", "ậ".repeat(25))))
                .hasMessageContaining("quá dài");
        verifyNoInteractions(otp, users, refreshTokens);
    }
}
