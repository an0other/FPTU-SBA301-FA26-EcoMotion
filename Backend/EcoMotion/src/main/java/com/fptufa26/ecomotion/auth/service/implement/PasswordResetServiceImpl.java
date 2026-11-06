package com.fptufa26.ecomotion.auth.service.implement;

import com.fptufa26.ecomotion.auth.dto.request.ResetPasswordRequest;
import com.fptufa26.ecomotion.auth.repository.RefreshTokenRepository;
import com.fptufa26.ecomotion.auth.service.PasswordResetService;
import com.fptufa26.ecomotion.common.exception.BadRequestException;
import com.fptufa26.ecomotion.notification.external.enums.NotificationChannel;
import com.fptufa26.ecomotion.otp.dto.request.OtpSendRequest;
import com.fptufa26.ecomotion.otp.dto.request.OtpVerifyRequest;
import com.fptufa26.ecomotion.otp.enums.OtpPurpose;
import com.fptufa26.ecomotion.otp.exception.OtpResendTooSoonException;
import com.fptufa26.ecomotion.otp.service.OtpService;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {
    private final UserService userService;
    private final OtpService otpService;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.password.min-length:8}")
    private int passwordMinLength = 8;

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
        if (request.password().length() < passwordMinLength) {
            throw new BadRequestException("Mật khẩu phải có ít nhất " + passwordMinLength + " ký tự");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("Mật khẩu quá dài. Vui lòng dùng mật khẩu ngắn hơn.");
        }
        String email = normalize(request.email());
        otpService.verify(new OtpVerifyRequest(email, request.otp(), OtpPurpose.PASSWORD_RESET));
        Long userId = userService.resetPassword(email, request.password());
        refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
