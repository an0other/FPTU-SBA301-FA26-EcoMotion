package com.fptufa26.ecomotion.auth.service.implement;

import com.fptufa26.ecomotion.auth.dto.request.FirstLoginChangePasswordRequest;
import com.fptufa26.ecomotion.auth.dto.request.ResetPasswordRequest;
import com.fptufa26.ecomotion.auth.exception.AccountInactiveException;
import com.fptufa26.ecomotion.auth.exception.AccountPendingException;
import com.fptufa26.ecomotion.auth.exception.InvalidCredentialsException;
import com.fptufa26.ecomotion.auth.repository.RefreshTokenRepository;
import com.fptufa26.ecomotion.auth.service.PasswordResetService;
import com.fptufa26.ecomotion.common.exception.BadRequestException;
import com.fptufa26.ecomotion.notification.external.enums.NotificationChannel;
import com.fptufa26.ecomotion.otp.dto.request.OtpSendRequest;
import com.fptufa26.ecomotion.otp.dto.request.OtpVerifyRequest;
import com.fptufa26.ecomotion.otp.enums.OtpPurpose;
import com.fptufa26.ecomotion.otp.exception.OtpResendTooSoonException;
import com.fptufa26.ecomotion.otp.service.OtpService;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {
    private final UserService userService;
    private final OtpService otpService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.password.min-length:8}")
    private int passwordMinLength = 8;

    @Autowired
    public PasswordResetServiceImpl(
            UserService userService,
            OtpService otpService,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.otpService = otpService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public PasswordResetServiceImpl(
            UserService userService,
            OtpService otpService,
            RefreshTokenRepository refreshTokenRepository) {
        this(userService, otpService, refreshTokenRepository, null);
    }

    @Override
    public void sendOtp(String email) {
        String normalized = normalize(email);
        if (userService.findByEmail(normalized).filter(user -> user.getStatus() == UserStatus.ACTIVE).isPresent()) {
            try {
                otpService.send(new OtpSendRequest(normalized, normalized,
                        OtpPurpose.PASSWORD_RESET, NotificationChannel.EMAIL));
            } catch (OtpResendTooSoonException ignored) {
            }
        }
    }

    @Override
    @Transactional
    public void reset(ResetPasswordRequest request) {
        validatePassword(request.password());
        String email = normalize(request.email());
        otpService.verify(new OtpVerifyRequest(email, request.otp(), OtpPurpose.PASSWORD_RESET));
        Long userId = userService.resetPassword(email, request.password());
        refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
    }

    @Override
    @Transactional
    public void changeTemporaryPassword(FirstLoginChangePasswordRequest request) {
        if (passwordEncoder == null) {
            throw new IllegalStateException("PasswordEncoder is required");
        }
        validatePassword(request.newPassword());
        String normalizedEmail = normalize(request.email());
        User user = userService.findByEmail(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);
        if (!user.isMustChangePassword()
                || !passwordEncoder.matches(request.temporaryPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!user.isActive()) {
            if (user.getStatus() == UserStatus.PENDING) {
                throw new AccountPendingException();
            }
            throw new AccountInactiveException();
        }
        if (request.newPassword().equals(request.temporaryPassword())) {
            throw new BadRequestException("Mật khẩu mới phải khác mật khẩu tạm thời");
        }
        Long userId = userService.completeFirstLoginPasswordChange(user.getEmail(), request.newPassword());
        refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < passwordMinLength) {
            throw new BadRequestException("Mật khẩu phải có ít nhất " + passwordMinLength + " ký tự");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("Mật khẩu quá dài. Vui lòng dùng mật khẩu ngắn hơn.");
        }
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
